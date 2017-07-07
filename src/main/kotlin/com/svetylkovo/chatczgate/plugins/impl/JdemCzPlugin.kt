package com.svetylkovo.chatczgate.plugins.impl

import com.svetylkovo.chatczgate.beans.rojo.PrivmsgCommand
import com.svetylkovo.chatczgate.irc.IrcLayer
import com.svetylkovo.chatczgate.plugins.Plugin
import com.svetylkovo.rojo.Rojo
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ini4j.Ini


class JdemCzPlugin : Plugin {

    override val name = "Jdem.cz"
    override var enabled: Boolean = false

    private val jdemUrl = "http://www.jdem.cz/get"
    private val client = OkHttpClient()

    private val urlMatcher = Rojo.matcher("http.?://\\S+")

    override fun init(config: Ini) {
        enabled = config.get("Jdem.cz", "enabled")?.toBoolean() ?: false
    }

    override fun processPrivmsg(privmsg: PrivmsgCommand, irc: IrcLayer) {
        val replacedMessage = urlMatcher.replace(privmsg.message) { url ->
            val reqUrl = HttpUrl.parse(jdemUrl)?.newBuilder()
                    ?.addQueryParameter("url", url)
                    ?.build()

            val request = Request.Builder().url(reqUrl).build()
            val resp = client.newCall(request).execute()
            if (resp.isSuccessful) {
                resp.body()?.string() ?: url
            } else {
                irc.replyNoticeAll("Couldn't shorten URL using Jdem.cz: $url")
                privmsg.message
            }
        }

        with(privmsg) {
            if (replacedMessage != message) {
                message = replacedMessage
                irc.replyNotice(target, "Jdem.cz: $replacedMessage")
            }
        }
    }

}