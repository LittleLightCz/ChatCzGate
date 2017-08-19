package com.svetylkovo.chatczgate

import com.svetylkovo.chatczgate.config.Config
import com.svetylkovo.chatczgate.irc.IrcLayer
import org.apache.log4j.PropertyConfigurator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket


object ChatCzGate {
    private val log: Logger = LoggerFactory.getLogger(ChatCzGate::class.java)

    val VERSION = "1.0.2"

    @JvmStatic
    fun main(args: Array<String>) {
        PropertyConfigurator.configure("log4j.properties")

        val port = Config.IRC_PORT

        log.info("*** ChatCzGate version $VERSION ***")
        log.info("Listening on port $port")
        val serverSocket = ServerSocket(port)

        while (true) {
            val connection = serverSocket.accept()
            log.info("Accepted connection from ${connection.inetAddress.hostAddress}")

            Thread(IrcLayer(connection)).start()
        }
    }
}