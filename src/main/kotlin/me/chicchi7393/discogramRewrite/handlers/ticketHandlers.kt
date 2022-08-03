package me.chicchi7393.discogramRewrite.handlers

import it.tdlight.jni.TdApi.*
import me.chicchi7393.discogramRewrite.JsonReader
import me.chicchi7393.discogramRewrite.discord.DsApp
import me.chicchi7393.discogramRewrite.mongoDB.DatabaseManager
import me.chicchi7393.discogramRewrite.objects.databaseObjects.TicketDocument
import me.chicchi7393.discogramRewrite.telegram.TgApp

class ticketHandlers {
    private val settings = JsonReader().readJsonSettings("settings")!!
    private val dbMan = DatabaseManager.instance
    private val dsClass = DsApp.instance
    private val tgClient = TgApp.instance

    fun startTicketWithFile(id: Long, chat: Chat, file: DownloadFile?, text: String) {
        dsClass.dsClient.getCategoryById(
            settings.discord["category_id"] as Long
        )!!.createTextChannel(
            "${settings.discord["IDPrefix"]}${dbMan.Utils().getLastUsedTicketId() + 1}"
        ).map { it ->
            dbMan.Create().Tickets().createTicketDocument(
                TicketDocument(
                    id,
                    it.idLong,
                    dbMan.Utils().getLastUsedTicketId() + 1,
                    mapOf("open" to true, "suspended" to false, "closed" to false),
                    System.currentTimeMillis() / 1000
                )
            )
            tgClient.downloadFile(chat.photo.small.id)
            Thread.sleep(500)
            val filePath = dsClass.getLastModified("session/database/profile_photos")!!.absolutePath
            dsClass.sendStartEmbed(
                chat,
                "File",
                dbMan.Utils().getLastUsedTicketId() + 1,
                it.idLong,
                filePath
            )
            if (file == null) {
                dsClass.sendTextMessageToChannel(
                    dbMan.Utils().searchAlreadyOpen(id)!!.channelId, text
                ).queue()
            } else {
                tgClient.client.send(file) {
                    dsClass.sendTextMessageToChannel(
                        dbMan.Utils().searchAlreadyOpen(id)!!.channelId, text
                    )
                        .addFile(java.io.File(it.get().local.path)).queue()
                }
            }
        }.queue()
    }

    fun startTicketWithText(chat: Chat, text: String) = dsClass.createTicket(chat, text)
    fun sendFileFollowMessage(id: Long, file: DownloadFile?, text: String) {
        if (file == null) {
            dsClass.sendTextMessageToChannel(
                dbMan.Utils().searchAlreadyOpen(id)!!.channelId,
                text
            ).queue()
        } else {
            tgClient.client.send(file) {
                dsClass.sendTextMessageToChannel(
                    dbMan.Utils().searchAlreadyOpen(id)!!.channelId,
                    text
                )
                    .addFile(java.io.File(it.get().local.path)).queue()
            }
        }
    }

    fun sendTextFollowMessage(id: Long, text: String) =
        dsClass.sendTextMessageToChannel(dbMan.Utils().searchAlreadyOpen(id)!!.channelId, text).queue()

    fun closeTicket(ticket: TicketDocument, text: String) {
        dbMan.Update().Tickets().closeTicket(
            ticket
        )
        tgClient.client.send(
            SendMessage(
                ticket.telegramId,
                0,
                0,
                null,
                null,
                InputMessageText(
                    FormattedText(
                        "Ticket chiuso. ${if (text != "") "Motivazione: $text" else ""}",
                        null
                    ), false, false
                )
            )
        ) {}
    }

    fun suspendTicket(ticket: TicketDocument, text: String) {
        dbMan.Update().Tickets().suspendTicket(
            ticket
        )
        tgClient.client.send(
            SendMessage(
                ticket.telegramId,
                0,
                0,
                null,
                null,
                InputMessageText(
                    FormattedText(
                        "Ticket sospeso. ${if (text != "") "Motivazione: $text" else ""}",
                        null
                    ), false, false
                )
            )
        ) {}
    }

    fun reOpenTicket(ticket: TicketDocument) {
        dbMan.Update().Tickets().reopenTicket(
            ticket
        )
        tgClient.client.send(
            SendMessage(
                ticket.telegramId,
                0,
                0,
                null,
                null,
                InputMessageText(FormattedText("Ticket riaperto.", null), false, false)
            )
        ) {}
    }
}