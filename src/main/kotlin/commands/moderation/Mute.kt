package commands.moderation

import commandhandler.CommandContext
import commands.BaseCommand
import commands.CommandType.Moderation
import database.muteRole
import ext.required
import ext.sendMuteLog
import ext.useArguments
import ext.useCommandProperly
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.requests.ErrorResponse

class Mute : BaseCommand(
    commandName = "mute",
    commandDescription = "Mute a member",
    commandType = Moderation,
    commandArguments = mapOf("member".required())
) {

    override fun execute(ctx: CommandContext) {
        super.execute(ctx)
        val args = ctx.args
        if (args.isNotEmpty()) {
            val user = args[0]
            val muteRole = guildId.muteRole
            if (muteRole.isNotEmpty()) {
                val role = ctx.guild.getRoleById(muteRole)
                if (role != null) {
                    addRole(args, user, role, ctx)
                    return
                }
            }

            channel.sendMessage("Mute role does not exist, creating...").queue {
                ctx.guild.createRole().setName("VancedHelper-Mute").queue({ role ->
                    val shardManager = ctx.event.jda.shardManager
                    ctx.guild.channels.forEach {
                        shardManager?.getTextChannelById(it.id)?.createPermissionOverride(role)?.deny(Permission.MESSAGE_WRITE)?.queue()
                    }
                    addRole(args, user, role, ctx)
                    guildId.muteRole = role.id
                }, ErrorHandler().handle(ErrorResponse.MAX_ROLES_PER_GUILD) {
                    channel.sendMessage("Guild reached maximum amount of roles!").queueAddReaction()
                })
            }
        } else {
            useArguments(1)
        }
    }

    private fun addRole(args: MutableList<String>, user: String, role: Role, ctx: CommandContext) {
        val reason = if (args.size > 1) args.apply { remove(user) }.joinToString(" ") else "no reason provided"
        val id = user.filter { it.isDigit() }
        if (id.isEmpty()) {
            useCommandProperly()
            return
        }
        if (contentIDRegex.matchEntire(id)?.value?.matches(contentIDRegex) == true) {
            ctx.guild.retrieveMemberById(id).queue({ member ->
                if (!ctx.authorAsMember?.canInteract(member)!!) {
                    channel.sendMessage("You can't mute this member!").queueAddReaction()
                    return@queue
                }
                ctx.guild.addRoleToMember(member, role).queue {
                    channel.sendMessage("Successfully muted ${member.asMention}").queueAddReaction()
                    ctx.authorAsMember?.let { it1 -> embedBuilder.sendMuteLog(member.user, it1.user, reason, guildId) }
                }
            }, ErrorHandler().handle(ErrorResponse.UNKNOWN_USER) {
                channel.sendMessage("Provided user does not exist!").queueAddReaction()
            })
        } else {
            useCommandProperly()
        }
    }

}