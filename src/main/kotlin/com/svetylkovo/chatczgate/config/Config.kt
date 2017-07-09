package com.svetylkovo.chatczgate.config

import org.ini4j.Ini
import java.io.File


object Config {

    val config = Ini(File("config.ini"))

    val IRC_PORT
        get() = config.get("IRC Server", "port")?.toInt() ?: 6667

    val IDLER_ENABLED
        get() = config.get("Idler", "enabled")?.toBoolean() ?: false

    val MAX_IDLE_MINUTES
        get() = config.get("Idler", "max_idle_minutes")?.toLong() ?: 30

    val IDLE_STRINGS
        get() = config.get("Idler", "idle_strings")?.split(",")?.toList() ?: listOf(".", "..")

    fun getIni() = config

}