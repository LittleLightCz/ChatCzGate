package com.svetylkovo.chatczgate

import org.ini4j.Ini
import java.io.File


object Config {

    val config = Ini(File("config.ini"))
    fun  getIrcPort() = config.get("IRC Server","port").toInt()


}