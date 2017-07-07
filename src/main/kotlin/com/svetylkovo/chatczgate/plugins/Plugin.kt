package com.svetylkovo.chatczgate.plugins

import com.svetylkovo.chatczgate.beans.rojo.PrivmsgCommand
import com.svetylkovo.chatczgate.irc.IrcLayer
import org.ini4j.Ini


interface Plugin {

    val name: String
    var enabled: Boolean

    fun init(config: Ini)

    fun processPrivmsg(privmsg: PrivmsgCommand, irc: IrcLayer)
}