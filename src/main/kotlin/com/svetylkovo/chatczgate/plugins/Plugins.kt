package com.svetylkovo.chatczgate.plugins

import com.svetylkovo.chatczgate.beans.RoomMessage
import com.svetylkovo.chatczgate.beans.rojo.PrivmsgCommand
import com.svetylkovo.chatczgate.config.Config
import com.svetylkovo.chatczgate.irc.IrcLayer
import org.reflections.Reflections


object Plugins {

    private var plugins: List<Plugin> = emptyList()

    private val enabledPlugins by lazy {
        plugins.filter { it.enabled }
    }

    init {
        val reflections = Reflections("com.svetylkovo.chatczgate.plugins.impl")

        plugins = reflections.getSubTypesOf(Plugin::class.java)
                .map { it.newInstance() }

        // Initialize plugins
        plugins.forEach { it.init(Config.getIni()) }
    }


    fun getLoadedPluginsInfo(): String = "Loaded plugins: " + plugins.map { it.name }.joinToString(", ")

    fun getDisabledPluginsInfo(): String = "Disabled plugins:" + plugins.filter { !it.enabled }
            .map { it.name }
            .joinToString(", ")

    fun processPrivmsg(privmsg: PrivmsgCommand, irc: IrcLayer) = enabledPlugins.forEach { it.processPrivmsg(privmsg, irc) }
    fun processRoomMessage(message: RoomMessage) = enabledPlugins.forEach { it.processRoomMessage(message) }
}