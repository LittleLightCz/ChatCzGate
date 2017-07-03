package com.svetylkovo.chatczgate

import com.svetylkovo.chatczgate.irc.IrcLayer
import org.apache.log4j.BasicConfigurator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket


object ChatCzGate {
    private val log: Logger = LoggerFactory.getLogger(ChatCzGate::class.java)

    val IRC_HOSTNAME = "localhost"

    val ENCODING = "UTF-8"
    val NEWLINE = "\r\n"
    val VERSION = "1.0.1"

    val IDLER_ENABLED = true //todo read from config
    val IDLE_TIME = 1000 //todo read from config
    val IDLER_STRINGS = listOf(".", "..") //todo read from config

    @JvmStatic
    fun main(args: Array<String>) {
        BasicConfigurator.configure();

        val port = Config.getIrcPort()

        log.info("*** ChatCzGate version $VERSION ***")
        log.info("Listening on port $port")
        val serverSocket = ServerSocket(port)

        while (true) {
            val connection = serverSocket.accept()
            Thread(IrcLayer(connection)).start()
        }
    }

}