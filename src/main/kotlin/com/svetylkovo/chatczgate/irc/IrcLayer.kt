package com.svetylkovo.chatczgate.irc

import com.svetylkovo.chatczgate.api.ChatApi
import com.svetylkovo.chatczgate.beans.IrcCommand
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.rojo.Rojo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Socket


class IrcLayer(conn: Socket) : Runnable, ChatEvent {

    private val log: Logger = LoggerFactory.getLogger(IrcLayer::class.java)

    private val reader = conn.getInputStream().reader().buffered()

    val chatApi = ChatApi(this)
    val nick = ""
    val userName = ""
    val password = ""
    val hostname = "chat.cz"

    //TODO val self.plugins = Plugins(config)

    private val commandMatcher = Rojo.of(IrcCommand::class.java)

    override fun run() {
        try {
            reader.use {
                while (true) {
                    val line = reader.readLine() ?: ""
                    log.debug("Received: $line")

                    if (!line.isEmpty()) {
                        handleClientInput(line)
                    } else {
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()

            if (chatApi.loggedIn) {
                log.info("We are still logged, logging out ...")
                chatApi.logout()
            }
        }
    }

    private fun handleClientInput(line: String) {

        commandMatcher.match(line).ifPresent { (command, args) ->
            log.info("Matched $command ===> $args")
        }


//        debug_args = re.sub(".", "*", args) if command == "PASS" else args
//        log.debug("Parsed command %s args: %s " % (command, debug_args))
//        self.handle_command(command.upper(), args)

    }


    override fun newMessage(room: Room, user: User, text: String, whisper: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun userJoined(room: Room, user: User) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun userLeft(room: Room, user: User) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun systemMessage(room: Room, message: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun userMode(room: Room, user: User, mode: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun kicked(room: Room) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}