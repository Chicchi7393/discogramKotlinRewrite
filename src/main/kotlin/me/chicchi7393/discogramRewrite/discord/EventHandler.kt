package me.chicchi7393.discogramRewrite.discord

import it.tdlight.jni.TdApi.*
import me.chicchi7393.discogramRewrite.JsonReader
import me.chicchi7393.discogramRewrite.discord.utils.reopenTicket
import me.chicchi7393.discogramRewrite.handlers.*
import me.chicchi7393.discogramRewrite.handlers.messageMenu.ticketMenu
import me.chicchi7393.discogramRewrite.mongoDB.DatabaseManager
import me.chicchi7393.discogramRewrite.objects.databaseObjects.MessageLinkType
import me.chicchi7393.discogramRewrite.objects.databaseObjects.TicketDocument
import me.chicchi7393.discogramRewrite.telegram.TgApp
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.bson.BsonTimestamp
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*


class EventHandler : ListenerAdapter() {
    private val settings = JsonReader().readJsonSettings()!!
    private val dbMan = DatabaseManager.instance
    private val tgClient = TgApp.instance.client
    private fun sendContent(tgId: Long, dsId: Long, content: InputMessageContent, ticket_id: Int, reply_id: Long) {
        var tg_reply = 0L
        if (reply_id != 0L) {
            tg_reply = dbMan.Search().MessageLinks().searchTgMessageByDiscordMessage(ticket_id, reply_id)
        }
        tgClient.send(
            SendMessage(tgId, 0, tg_reply, null, null, content)
        ) {
            dbMan.Update().MessageLinks().addMessageToMessageLinks(
                ticket_id,
                MessageLinkType(it.get().id, dsId, BsonTimestamp(System.currentTimeMillis() / 1000))
            )
        }
    }

    private fun downloadFile(url: URL, filename: String): Path {
        val path = Files.createDirectory(
            Path.of(
                System.getProperty("java.io.tmpdir") + "/${
                    Random().nextInt(100000000, 999999999)
                }"
            )
        ).toString()
        url.openStream().use { Files.copy(it, Path.of("$path/$filename")) }
        return Path.of("$path/$filename")
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonHandler = buttonHandlers(event)
        val ticketMenu = ticketMenu(event)
        when {
            event.componentId.startsWith("closeNoRating") -> buttonHandler.closeButtonTicketHandler(false)
            event.componentId.startsWith("close") -> buttonHandler.closeButtonTicketHandler(true)
            event.componentId.startsWith("suspend") -> buttonHandler.suspendButtonTicketHandler()
            event.componentId.startsWith("assign") -> buttonHandler.assignButtonTicketHandler()
            event.componentId.startsWith("menu") -> buttonHandler.menuButtonHandler()
            event.componentId.startsWith("MenuButton-ticket-removeTicket") -> ticketMenu.removeTicket()
            event.componentId.startsWith("MenuButton-ticket-marisaTicket") -> ticketMenu.marisaTicket()
            else -> {}
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val modalHandlers = modalHandlers(event)
        when {
            event.modalId.startsWith("closeModal") -> modalHandlers.closeTicketModal(true)
            event.modalId.startsWith("closeWRModal") -> modalHandlers.closeTicketModal(false)
            event.modalId.startsWith("suspendModal") -> modalHandlers.suspendTicketHandler()
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val ticket = try {
            dbMan.Search().Tickets()
                .searchTicketDocumentByChannelId(event.channel.idLong)!!
        } catch (e: Exception) {
            TicketDocument(0L, 0L, 0, mapOf("open" to false, "suspended" to false, "closed" to true), 0L)
        }
        val tgId = ticket.telegramId
        if (
            !event.isFromType(ChannelType.PRIVATE) &&
            event.channel.name.startsWith(settings.discord["idPrefix"] as String, true) &&
            !event.author.isBot &&
            !event.message.contentRaw.startsWith(settings.discord["ignore_message_prefix"] as String)
        ) {
            if ((event.author.idLong == dbMan.Search().Assignee()
                    .searchAssigneeDocumentById(
                        event.channel.name.split(settings.discord["idPrefix"] as String)[1].split(
                            " "
                        )[0].toInt()
                    )!!.modId || DsApp.instance.isHigherRole(event.member!!))
                && ticket.status["open"] == true
            ) {
                if (ticket.status["suspended"] == true
                ) {
                    reopenTicket().reopenTicket(tgId)
                }
                if (event.message.attachments.isEmpty()) {
                    sendContent(
                        tgId,
                        event.messageIdLong,
                        InputMessageText(FormattedText(event.message.contentRaw, null), false, true),
                        ticket.ticketId,
                        if (event.message.referencedMessage != null) event.message.referencedMessage!!.idLong else 0L
                    )
                } else if (event.message.attachments.isNotEmpty()) {
                    var i = 0
                    for (attach in event.message.attachments) {
                        val path = downloadFile(URL(attach.url), attach.fileName).toString()
                        i++
                        sendContent(
                            tgId,
                            event.messageIdLong,
                            InputMessageDocument(
                                InputFileLocal(path),
                                null,
                                false,
                                FormattedText(if (i == 1) event.message.contentRaw else "", null)
                            ),
                            ticket.ticketId,
                            if (event.message.referencedMessage != null) event.message.referencedMessage!!.idLong else 0L
                        )
                    }
                }
            } else if ((event.author.idLong != dbMan.Search().Assignee()
                    .searchAssigneeDocumentById(
                        event.channel.name.split(settings.discord["idPrefix"] as String)[1].split(
                            " "
                        )[0].toInt()
                    )!!.modId &&
                        !event.message.contentRaw.startsWith(settings.discord["ignore_message_prefix"] as String)) || (ticket.status["open"] == false && !event.message.contentRaw.startsWith(
                    settings.discord["ignore_message_prefix"] as String
                ))
            ) {
                event.message.delete().queue()
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        slashCommandHandlers(event).onSlashCommand()
    }

    override fun onMessageContextInteraction(event: MessageContextInteractionEvent) {
        messageCommandHandler(event).handle()
    }

    override fun onMessageDelete(event: MessageDeleteEvent) {
        MessageModifyHandler(event).onMessageDelete()
    }

    override fun onMessageUpdate(event: MessageUpdateEvent) {
        MessageModifyHandler(event).onMessageUpdate()
    }

    override fun onSelectMenuInteraction(event: SelectMenuInteractionEvent) {
        MenuInteractionHandler(event).onSelectMenuInteraction()
    }
}