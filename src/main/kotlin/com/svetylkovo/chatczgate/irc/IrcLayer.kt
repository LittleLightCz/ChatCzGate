package com.svetylkovo.chatczgate.irc

import com.svetylkovo.chatczgate.ChatCzGate.VERSION
import com.svetylkovo.chatczgate.api.ChatApi
import com.svetylkovo.chatczgate.beans.Gender
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.beans.rojo.IrcCommand
import com.svetylkovo.chatczgate.beans.rojo.PrivmsgCommand
import com.svetylkovo.chatczgate.cache.UsersCache
import com.svetylkovo.chatczgate.config.Config
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.extensions.fromWhitespace
import com.svetylkovo.chatczgate.extensions.toWhitespace
import com.svetylkovo.chatczgate.plugins.Plugins
import com.svetylkovo.rojo.Rojo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Socket


class IrcLayer(conn: Socket) : Runnable, ChatEvent {

    private val NEWLINE = "\r\n"

    private val log: Logger = LoggerFactory.getLogger(IrcLayer::class.java)

    private val reader = conn.getInputStream().reader().buffered()
    private val writer = conn.getOutputStream().writer().buffered()

    val chatApi = ChatApi(this)

    var nick = ""
        get() = if (field.isEmpty()) userName else field

    var userName = ""
    var password = ""
    var hostname = "chat.cz"

    var run = true

    private val commandMatcher = Rojo.of(IrcCommand::class.java)
    private val kickMatcher = Rojo.matcher("#(\\S+)\\s+(\\S+)\\s+:(.+)")
    private val modeMatcher = Rojo.matcher("#([^ ]+) \\+(\\w+) (.+)")
    private val passMatcher = Rojo.matcher("^:?(.+)")
    private val privmsgMatcher = Rojo.of(PrivmsgCommand::class.java)
    private val whoisMatcher = Rojo.matcher("[^ ]+")
    private val firstNonBlank = Rojo.matcher("^\\S+")

    override fun run() {
        try {
            reader.use {
                while (run) {
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
            handleCommand(command, args)
        }
    }

    private fun handleCommand(command: String, args: String) {
        if (command != "PASS") {
            log.info("IRC command: $command, args: $args")
        } else {
            log.info("Received PASS command")
        }

        try {
            when (command.toUpperCase()) {
                "PASS" -> handlePass(args)
                "NICK" -> handleNick(args)
                "USER" -> handleUser(args)
                "LIST" -> handleList()
                "JOIN" -> handleJoin(args)
                "PART" -> handlePart(args)
                "WHO" -> handleWho(args)
                "WHOIS" -> handleWhois(args)
                "PING" -> handlePing(args)
                "PRIVMSG" -> handlePrivmsg(args)
                "OPER" -> handleOper(args)
                "MODE" -> handleMode(args)
                "TOPIC" -> handleTopic(args)
                "NAMES" -> handleNames(args)
                "INVITE" -> handleInvite(args)
                "KICK" -> handleKick(args)
                "QUIT" -> handleQuit()
                else -> log.warn("Unrecognized command: $command")
            }
        } catch (t: Throwable) {
            log.error("Error during command handling!", t)
        }
    }

    private fun handleQuit() {
        chatApi.logout()
        run = false
    }

    private fun handleUser(args: String) {
        val parsedUsername = Rojo.find("^\\S+", args).orElse("")
        if (!parsedUsername.isEmpty()) {
            userName = parsedUsername
        } else {
            notEnoughArgsReply("USER $args")
        }
    }

    private fun handleNick(args: String) {
        nick = args

        // When NICK is received, perform login
        try {
            if (!password.isEmpty()) {
                chatApi.login(nick, password)
            } else {
                // Todo Solve male/female problem
                chatApi.loginAnonymously(nick, Gender.MALE)
            }

            sendWelcomeMessage()
        } catch(t: Throwable) {
            log.error("Failed to login!", t)
            // Todo: send wrong password reply
        }
    }

    private fun handlePass(args: String) {
        passMatcher.firstGroup(args)
                .findFirst()
                .ifPresent { password = it }
    }

    private fun handleList() {
        //args not supported yet
        reply(321, "Channel :Users  Name")
        chatApi.getRoomList().forEach { room ->
            reply(322, "#${room.name.toWhitespace()} ${room.usersCount} :${room.description}")
        }
        reply(323, ":End of /LIST")
    }

    private fun handleJoin(args: String) {
        firstNonBlank.find(args).ifPresent {
            it.split(",")
                .map { it.replaceFirst("#","") }
                .forEach { roomName ->
                    val room = chatApi.getRoomByName(roomName.fromWhitespace())

                    if (room != null) {
                        log.info("Joining room : ${room.name}")
                        chatApi.join(room)

                        socketSend(":$nick!$userName@$hostname JOIN #$roomName")
                        reply(332, "#$roomName :${room.description}")

                        val usersInRoom =  room.users
                                .map { it.nick.toWhitespace() }
                                .joinToString(" ")

                        reply(353, "= #$roomName :$nick $usersInRoom")
                        reply(366, "#$roomName :End of /NAMES list.")
                    } else {
                        log.error("Couldn't find the room for name: $roomName")
                    }
                }
        }
    }

    private fun handlePart(args: String) {
        firstNonBlank.find(args).ifPresent {
            it.split(",").forEach { channel ->
                val roomName = channel.removePrefix("#")
                val room = chatApi.getRoomByName(roomName.fromWhitespace())
                if (room != null) {
                    log.info("Leaving room : ${room.name}")
                    chatApi.part(room)
                    replyPart(nick, "#$roomName")
                } else {
                    log.error("Couldn't find the room for name: $roomName")
                }
            }
        }
    }

    private fun handleWho(args: String) {
        firstNonBlank.find(args).ifPresent { channel ->
            val roomName = channel.removePrefix("#")
            val room = chatApi.getRoomByName(roomName.fromWhitespace())

            //update room users
            room?.let { chatApi.updateRoomInfo(it) }

            room?.users?.forEach { user ->
                sendWhoUserInfo(room, user)
            }

            reply(315, ":End of WHO list")

            room?.users?.forEach { user ->
                setUserMode(user, room)
            }
        }
    }

    private fun handlePrivmsg(args: String) {
        privmsgMatcher.match(args).ifPresent { command ->
            try {
                Plugins.processPrivmsg(command, this)
                val (target, message) = command

                if (target.startsWith("#")) {
                    val roomName = target.removePrefix("#").fromWhitespace()
                    val room = chatApi.getActiveRoomByName(roomName)
                    room?.let { chatApi.say(it, message) }
                } else {
                    chatApi.whisper(target.fromWhitespace(), message)
                }
            } catch (t: Throwable) {
                replyPrivmsg("ChatCzGate", nick, t.message ?: "Error while trying to send the message")
            }
        }
    }

    private fun handlePing(args: String) = socketSend(":$hostname PONG $hostname :$args")

    private fun handleWhois(args: String) {
        whoisMatcher.find(args).ifPresent { nick ->
            val userProfile = chatApi.getUserProfile(nick.fromWhitespace())
            if (userProfile != null) {
                val (user, profile) = userProfile

                replyNoticeAll("=== WHOIS Profile ===")
                replyNoticeAll("Anonym: ${user?.anonymous}")
                replyNoticeAll("Nick: ${user?.nick}")

                profile?.age?.let { replyNoticeAll("Věk: $it") }

                replyNoticeAll("Pohlaví: ${user?.gender?.name}")

                profile?.profileViewCount?.let { replyNoticeAll("Profil zobrazen: ${it}x") }

                replyNoticeAll("Online: ${user?.online}")

                user?.rooms?.let { rooms ->
                    replyNoticeAll("Chatuje v: ${rooms.joinToString(", ").toWhitespace()}")
                }

                if (user?.anonymous == false) {
                    replyNoticeAll("Profil: ${user.profileUrl}")
                }

                replyNoticeAll("=== End Of WHOIS Profile ===")
            } else {
                replyNoticeAll("WHOIS: Failed to get profile of: $nick")
            }
        }
    }

    private fun handleMode(args: String) {
        modeMatcher.forEach(args) { roomName, modes, nick ->
            val room = chatApi.getActiveRoomByName(roomName.fromWhitespace())

            if (room != null && modes.contains("o")) {
                chatApi.admin(room, nick.fromWhitespace())
                // Now remove the half-operator from user, that was former half-operator
                UsersCache.getByUid(room.operatorId)?. let { user ->
                    replyMode("#$roomName", "-h", user.nick.toWhitespace())
                }
            } else {
                log.error("Failed to get room by name: $roomName")
            }
        }
    }

    private fun handleKick(args: String) {
        kickMatcher.forEach(args) { roomName, user, reason ->
            try {
                val room = chatApi.getActiveRoomByName(roomName)
                if (room != null) {
                    chatApi.kick(room, user, reason)
                } else {
                    log.error("Failed to get room by name: $roomName")
                }
            } catch (t: Throwable) {
                val message = "Failed to kick user $user from the room $roomName!"
                log.error(message, t)
                replyNoticeAll(message)
            }
        }
    }

    private fun handleOper(args: String) {
        log.info("OPER command handler not implemented. Arguments: ${args}")
    }

    private fun handleTopic(args: String) {
        log.info("TOPIC command handler not implemented. Arguments: ${args}")
    }

    private fun handleNames(args: String) {
        log.info("NAMES command handler not implemented. Arguments: ${args}")
    }

    private fun handleInvite(args: String) {
        log.info("INVITE command handler not implemented. Arguments: ${args}")
    }

    @Synchronized
    private fun socketSend(message: String) {
        log.debug("Sending: $message")
        writer.write("$message $NEWLINE")
        writer.flush()
    }

    fun replyJoin(name: String, channel: String) = socketSend(":$name JOIN $channel")

    fun replyPart(name: String, channel: String) = socketSend(":$name PART $channel")

    fun replyPrivmsg(sender: String, to: String, text: String) = socketSend(":$sender PRIVMSG $to :$text")

    fun replyNotice(channel: String, message: String) = socketSend(":$hostname NOTICE $channel :$message")

    fun replyNoticeAll(message: String) {
        chatApi.getActiveRoomNames().forEach { replyNotice("#$it".toWhitespace(), message) }
    }

    fun replyMode(channel: String, mode: String, nick: String) = socketSend(":$hostname MODE $channel $mode $nick")

    fun replyKick(channel: String, reason: String) = socketSend(":$hostname KICK $channel $nick $reason")

    fun notEnoughArgsReply(commandName: String) = reply(461, "$commandName :Not enough parameters")

    private fun reply(responseNumber: Int, message: String) {
        val formattedResp = String.format("%03d", responseNumber)
        socketSend(":$hostname $formattedResp $nick $message")
    }

    private fun sendMotd(text: String) = reply(372, ":- $text")

    private fun sendWelcomeMessage() {

        val welcomeMessage = """:
          ____ _           _
        /  ___| |__   __ _| |_   ___ ____
        | |   | '_ \ / _` | __| / __|_  /
        | |___| | | | (_| | |_ | (__ / /
         \____|_| |_|\__,_|\__(_)___/___|

        ########################################

        Welcome to the ChatCzGate ${VERSION}!

        Website: https://github.com/LittleLightCz/ChatCzGate
        Credits: Svetylk0, Imrija

        Idler enabled: ${Config.IDLER_ENABLED}
        Idler minutes: ${Config.MAX_IDLE_MINUTES}
        Idle strings: ${Config.IDLE_STRINGS.joinToString(", ")}

        ${Plugins.getLoadedPluginsInfo()}
        ${Plugins.getDisabledPluginsInfo()}

        Have fun! :-)
        """

        reply(1, welcomeMessage)
        reply(2, ":You're running version $VERSION")
        reply(3, ":")
        reply(4, "")
        reply(375, "Message of the day -")
        sendMotd("With great power comes great responsibility ...")
        reply(376, "$nick :End of MOTD command.")
    }

    private fun sendWhoUserInfo(room: Room, user: User) {
        val roomName = room.name.toWhitespace()
        val nick = user.nick.toWhitespace()
        val gender = user.gender.value
        val op = "" // Admin SS DS ?
        socketSend("#$roomName $nick@$hostname unknown $hostname $nick $gender $op:0 $nick")
    }

    /**
     * Sets the right mode for specific user
     */
    private fun setUserMode(user: User, room: Room) {
        val nick = user.nick.toWhitespace()
        val channel = "#${room.name}".toWhitespace()

        // Mark girl
        if (user.gender == Gender.FEMALE){
            replyMode(channel, "+v", nick)
        }

        // Mark operator
        if (room.admins.contains(user.nick)) {
            replyMode(channel, "+o", nick)
        }

        // Mark half-operator
        if (user.uid == room.operatorId) {
            replyMode(channel, "+h", nick)
        }

        // Mark room admin
        if (user.adminId != null) {
            replyMode(channel, "+A", nick)
        }
    }

    override fun newMessage(room: Room, user: User, text: String) {
        if (user.nick != nick) {
            val to = "#${room.name}"
            replyPrivmsg(user.nick.toWhitespace(), to.toWhitespace(), text)
        }
    }

    override fun newPrivateMessage(user: User, text: String) {
        if (user.nick != nick) {
            replyPrivmsg(user.nick.toWhitespace(), nick.toWhitespace(), text)
        }
    }

    override fun userJoined(room: Room, user: User) {
        if (user.nick != nick) {
            val nickName = user.nick.toWhitespace()
            val channel = "#${room.name}".toWhitespace()

            replyJoin(nickName, channel)
            setUserMode(user, room)

            if (user.anonymous) {
                replyNotice(channel, "INFO: $nickName is anonymous")
            }
        }
    }

    override fun userLeft(room: Room, user: User) {
        if (user.nick != nick) {
            replyPart(user.nick.toWhitespace(), "#${room.name}".toWhitespace())
        }
    }

    override fun systemMessage(room: Room, message: String) = replyNotice("#${room.name}".toWhitespace(), message)

    override fun userMode(room: Room, user: User, mode: String) =
            replyMode("#${room.name}".toWhitespace(), mode, user.nick.toWhitespace())

    override fun kicked(room: Room) = replyKick("#${room.name}".toWhitespace(), "Reason not implemented yet!")

}

