package me.chicchi7393.discogramRewrite.http.handlers.get

import io.javalin.http.Context
import me.chicchi7393.discogramRewrite.http.handlers.HTTPHandlerClass
import me.chicchi7393.discogramRewrite.mongoDB.DatabaseManager

class isUnderage : HTTPHandlerClass() {
    override var path = "/isUnderage"
    private val dbMan = DatabaseManager.instance
    override fun handle(ctx: Context): Context {
        return ctx.json(mapOf("result" to dbMan.Utils().isUserUnderage(ctx.body().toLong())))
    }
}