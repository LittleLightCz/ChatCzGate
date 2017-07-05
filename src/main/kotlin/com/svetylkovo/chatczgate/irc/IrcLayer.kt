package com.svetylkovo.chatczgate.irc

import com.svetylkovo.chatczgate.ChatCzGate
import com.svetylkovo.chatczgate.ChatCzGate.VERSION
import com.svetylkovo.chatczgate.api.ChatApi
import com.svetylkovo.chatczgate.beans.Gender
import com.svetylkovo.chatczgate.beans.IrcCommand
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.extensions.fromWhitespace
import com.svetylkovo.chatczgate.extensions.toWhitespace
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

    //TODO val self.plugins = Plugins(config)

    private val commandMatcher = Rojo.of(IrcCommand::class.java)
    private val passMatcher = Rojo.matcher("^:?(.+)")
    private val privmsgMatcher = Rojo.matcher("(.+?)\\s*:(.*)")
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

        when (command) {
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
//            "MODE" -> handleMode(args)
            "TOPIC" -> handleTopic(args)
            "NAMES" -> handleNames(args)
            "INVITE" -> handleInvite(args)
//            "KICK" -> handleKick(args)
            "QUIT" -> handleQuit()
            else -> log.warn("Unrecognized command: $command")
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
            it.split(",").forEach { roomName ->
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
        firstNonBlank.find(args).ifPresent { roomName ->
            val room = chatApi.getRoomByName(roomName.fromWhitespace())

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
        privmsgMatcher.forEach(args) { target, message ->
            try {
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
                    replyNoticeAll("Profil: ${user?.profileUrl}")
                }

                replyNoticeAll("=== End Of WHOIS Profile ===")
            } else {
                replyNoticeAll("WHOIS: Failed to get profile of: $nick")
            }
        }
    }

/*


        def mode_handler():
            m = next(re.finditer(r"#([^ ]+) \+(\w+) (.+)", args), None)
            if m:
                room_name, modes, nick = m.groups()
                room = self.chatapi.get_active_room_by_name(from_ws(room_name))

                if room:
                    if "o" in modes:
                        last_admin_id = room.operator_id
                        self.chatapi.admin(room, from_ws(nick))
                        # Now remove the half-operator from user, that was former half-operator
                        user = UserDb.get_user_by_uid(last_admin_id)
                        if user:
                            self.reply_mode("#"+room_name, "-h", to_ws(user.name))
                else:
                    log.error("Failed to get room by name: "+room_name)


        def kick_handler():
            match = re.match(r"#(\S+)\s+(\S+)\s+:(.+)", args)

            try:
                if match:
                    room_name = from_ws(match.group(1))
                    user = from_ws(match.group(2))
                    reason = match.group(3)

                    room = self.chatapi.get_active_room_by_name(room_name)
                    if room:
                        self.chatapi.kick(room, user, reason)
                    else:
                        log.error("Failed to get room by name: "+room_name)
            except:
                msg = "Failed to kick user {0} from the room {1}!".format(user, room_name)
                log.exception(msg)
                self.reply_notice_all(msg)
 */
    private fun handleOper(args: String) {
        log.info("OPER command handler not implemented")
    }

    private fun handleTopic(args: String) {
        log.info("TOPIC command handler not implemented")
    }

    private fun handleNames(args: String) {
        log.info("NAMES command handler not implemented")
    }

    private fun handleInvite(args: String) {
        log.info("INVITE command handler not implemented")
    }

    private fun socketSend(message: String) {
        //TODO handle plugins:        data = self.plugins.process(PluginData(reply=response))
        log.debug("Sending: $message")
        writer.write("$message $NEWLINE")
        writer.flush()
    }

    private fun replyJoin(name: String, channel: String) = socketSend(":$name JOIN $channel")

    private fun replyPart(name: String, channel: String) = socketSend(":$name PART $channel")

    private fun replyPrivmsg(sender: String, to: String, text: String) = socketSend(":$sender PRIVMSG $to :$text")

    private fun replyNotice(channel: String, message: String) = socketSend(":$hostname NOTICE $channel :$message")

    private fun replyNoticeAll(message: String) {
        chatApi.getActiveRoomNames().forEach { replyNotice("#$it".toWhitespace(), message) }
    }

    private fun replyMode(channel: String, mode: String, nick: String) = socketSend(":$hostname MODE $channel $mode $nick")

    private fun replyKick(channel: String, reason: String) = socketSend(":$hostname KICK $channel $nick")

    private fun reply(responseNumber: Int, message: String) {
        val formattedResp = String.format("%03d", responseNumber)
        socketSend(":$hostname $formattedResp $nick $message")
    }

    private fun notEnoughArgsReply(commandName: String) = reply(461, "$commandName :Not enough parameters")

    private fun sendMotd(text: String) = reply(372, ":- $text")

    private fun sendWelcomeMessage() {
        val loadedPlugins = "loaded..." //todo self.plugins.get_loaded_plugins_names()
        val disabledPlugins = "disabled..." //todo self.plugins.get_disabled_plugins_names()

        val welcomeMessage = """:
        ____ _           _
        / ___| |__   __ _| |_   ___ ____
        | |   | '_ \ / _` | __| / __|_  /
        | |___| | | | (_| | |_ | (__ / /
        \____|_| |_|\__,_|\__(_)___/___|

        ########################################

        Welcome to the ChatCzGate ${VERSION}!

        Website: https://github.com/LittleLightCz/ChatCzGate
        Credits: Svetylk0, Imrija

        Idler enabled: ${ChatCzGate.IDLER_ENABLED}
        Idle time: ${ChatCzGate.IDLE_TIME}
        Idler strings: ${ChatCzGate.IDLER_STRINGS.joinToString(",")}

        Loaded plugins: $loadedPlugins
        Disabled plugins: $disabledPlugins

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
        if (room.admins.contains(user.nick))
            replyMode(channel, "+o", nick)

        // Mark half-operator
        if (user.uid == room.operatorId) {
            replyMode(channel, "+h", nick)
        }

        // Mark room admin
        if (user.adminId != null) {
            replyMode(channel, "+A", nick)
        }

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