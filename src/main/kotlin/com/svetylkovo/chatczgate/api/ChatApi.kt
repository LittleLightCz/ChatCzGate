package com.svetylkovo.chatczgate.api

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.svetylkovo.chatczgate.beans.*
import com.svetylkovo.chatczgate.cache.UsersCache
import com.svetylkovo.chatczgate.config.Config
import com.svetylkovo.chatczgate.config.Config.IDLER_ENABLED
import com.svetylkovo.chatczgate.config.Config.MAX_IDLE_MINUTES
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.plugins.Plugins
import com.svetylkovo.chatczgate.service.ChatService
import org.apache.commons.lang3.StringEscapeUtils
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

class ChatApi(private val chatEvent: ChatEvent) {

    private val STORED_MESSAGE_DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")

    private val STORED_MESSAGES_CHECK_INTERVAL: Long = 2 * 60 * 1000
    private val MESSAGES_CHECK_INTERVAL: Long = 5 * 1000
    private val USERS_CHECK_INTERVAL: Long = 50 * 1000

    private val log: Logger = LoggerFactory.getLogger(ChatApi::class.java)

    private val service = ChatService()
    private val rooms = ArrayList<Room>()

    private val timer = Timer()
    private val mapper = ObjectMapper()

    var loggedIn = false

    private var idleStrings = Config.IDLE_STRINGS

    init {
        log.info("Getting cookies ...")
        service.pingLoginPage()

        timer.schedule(0, USERS_CHECK_INTERVAL) {
            usersCheck()
        }

        timer.schedule(0, MESSAGES_CHECK_INTERVAL) {
            messagesCheck()
        }

        timer.schedule(0, STORED_MESSAGES_CHECK_INTERVAL) {
            storedMessagesCheck()
        }
    }

    @Synchronized
    private fun usersCheck() {
        try {
            if (loggedIn) {
                service.getChatHeader()
                rooms.forEach { service.pingRoomUserTime(it) }
            }
        } catch (t: Throwable) {
            log.error("Error during pinging users' room time!", t)
        }
    }

    @Synchronized
    private fun messagesCheck() {
        var lastRoom: Room? = null
        try {
            for (room in rooms) {
                lastRoom = room

                log.debug("Checking for new messages in: ${room.name}")
                service.getRoomText(room)?.let { response ->
                    processRoomMessages(room, response)
                }
                triggerIdler(room)
            }
        } catch (e: JsonMappingException) {
            if (e.message?.startsWith("Can not deserialize instance of") == true) {
                lastRoom?.let { room ->
                    chatEvent.kicked(room)
                    part(room)
                }
            }
            log.error("JSON mapping failed", e)
        } catch (t: Throwable) {
            log.error("Error during new messages check!", t)
        }
    }

    private fun storedMessagesCheck() {
        try {
            log.debug("Checking for stored messages ...")

            service.getChatHeader()?.headerData?.msgCount?.let { msgCount ->

                if (msgCount > 0) {
                    service.pingStoredMessagesPage()

                    service.getStoredMessagesUsers()
                            ?.asSequence()
                            ?.map { it.uid }
                            ?.map { service.getStoredMessages(it)?.storedMessages }
                            ?.filterNotNull()
                            ?.flatten()
                            ?.filter { !it.fromYourself }
                            ?.sortedByDescending { it.date }
                            ?.take(msgCount)
                            ?.sortedBy { it.date }
                            ?.forEach { message ->
                                message.userFromUid?.let { uid ->
                                    UsersCache.getByUid(uid)?.let { user ->
                                        val msgDate = STORED_MESSAGE_DATE_FORMAT.format(message.date)
                                        chatEvent.newPrivateMessage(user, "[MESSAGE - $msgDate] ${message.text}")
                                    }
                                }
                            }
                }
            }
        } catch (t: Throwable) {
            log.error("Error during stored messages check!", t)
        }
    }

    @Synchronized
    private fun getUserByName(name: String) =
            rooms.mapNotNull { it.getUserByName(name) }
                    .firstOrNull()
                    ?: UsersCache.getByName(name)


    @Synchronized
    fun getRoomByName(name: String): Room? = getRoomList().find { it.name == name }

    @Synchronized
    fun getActiveRoomByName(name: String): Room? = rooms.find { it.name == name }

    @Synchronized
    fun getActiveRoomNames(): List<String> = rooms.map { it.name }

    fun getRoomList(): List<Room> {
        log.info("Downloading room list ...")
        return service.getRoomList()
                ?.map { it.toRoom() }
                ?.sortedBy { it.name }
                ?: emptyList()
    }

    fun login(user: String, password: String) {
        log.info("Logging as: $user")
        val response = service.login(user, password)
        loginCheck(response)
    }

    fun loginAnonymously(user: String, gender: Gender) {
        log.info("Logging anonymously as: $user (${gender.value})")
        val response = service.loginAnonymously(user, gender)
        loginCheck(response)
    }

    private fun loginCheck(resp: String) {
        val html = Jsoup.parse(resp)
        val alert = html.select("div[class=alert]")
                .firstOrNull()

        loggedIn = false

        when {
            isLoggedPage(resp) -> {
                log.info("Login successful!")
                loggedIn = true
            }
            alert != null -> throw RuntimeException(alert.text().trim())
            else -> throw RuntimeException("Failed to login for unknown reason.")
        }
    }

    private fun isLoggedPage(html: String): Boolean {
        return Jsoup.parse(html)
                .select("li#nav-user")
                .first() != null
    }

    private fun getIdlerMessage(lastMessage: String): String {
        val idleString = idleStrings.firstOrNull { it != lastMessage } ?: "..."

        Collections.rotate(idleStrings, 1)
        return idleString
    }

    private fun triggerIdler(room: Room) {
        if (IDLER_ENABLED && shouldIdle(room)) {
            val msg = getIdlerMessage(room.lastMessage)
            say(room, msg)
            chatEvent.systemMessage(room, "IDLER: $msg")
        }
    }

    private fun shouldIdle(room: Room) = System.currentTimeMillis() - room.timestamp > MAX_IDLE_MINUTES * 60 * 1000

    @Synchronized
    private fun removeRoom(room: Room) {
        rooms.removeIf { it.roomId == room.roomId }
    }

    /**
     * Adds admin rights to nick
     */
    fun admin(room: Room, nick: String) {
        say(room, "/admin $nick")
    }

    @Synchronized
    fun whisper(toNick: String, text: String) {
        rooms.firstOrNull()?.let { say(it, text, toNick) }
                ?: throw RuntimeException("Failed to create a whisper message! There are no active rooms. Join the room first!")
    }

    fun kick(room: Room, user: String, reason: String) {
        say(room, "/kick $user $reason")
    }

    @Synchronized
    private fun processRoomMessages(room: Room, resp: RestResponse) {
        if (resp.status == 200) {
            resp.data?.index?.let { room.chatIndex = it }
            resp.data?.data?.forEach { processMessage(room, it) }
        } else {
            when (resp.statusMessage) {
                "User in room NOT_FOUND" -> {
                    chatEvent.kicked(room)
                    removeRoom(room)
                }
                else -> {
                    log.error("Failed to get new messages: ${resp.statusMessage}")
                }
            }
        }
    }

    @Synchronized
    private fun processMessage(room: Room, message: RoomMessage) {

        Plugins.processRoomMessage(message)

        when (message.s) {
            "enter" -> {
                message.user?.let { user ->
                    room.addUser(user)
                    chatEvent.userJoined(room, user)
                }
            }
            "leave", "auto_leave" -> {
                room.getUserByUid(message.uid)?.let { user ->
                    room.removeUser(user)
                    chatEvent.userLeft(room, user)
                }
            }
            "cli" -> chatEvent.systemMessage(room, message.t)
            "user", "friend", "userSetting" -> {}
            "admin" -> {
                UsersCache.getByName(message.nick)?.let { user ->
                    updateRoomInfo(room)
                    if (room.operatorId == user.uid) {
                        chatEvent.userMode(room, user, "+h")
                    }
                }
            }
            "" -> {
            } //ignore
            is String -> log.warn("Unknown system message: \n${mapper.writeValueAsString(message)}")
            null -> {
                //Standard message
                val user = UsersCache.getByUid(message.uid)

                if (user != null) {
                    val text = StringEscapeUtils.unescapeHtml4(message.t)

                    if (message.w != null) {
                        if (message.to == 0 && room == rooms.first()) {
                            chatEvent.newPrivateMessage(user, text)
                        }
                    } else {
                        chatEvent.newMessage(room, user, text)
                    }
                } else {
                    val warnMessage = "Unknown UID: ${message.uid} -> ${message.t}"
                    log.warn(warnMessage)
                    chatEvent.systemMessage(room, "WARNING: $warnMessage")
                }
            }
        }
    }

    fun getUserProfile(userName: String) =
            getUserByName(userName)?.uid?.let { uid ->
                val user = service.getUserById(uid)
                val profile = service.getUserProfile(uid)
                UserProfile(user, profile)
            }

    fun logout() {
        if (service.logout().contains("Úspěšné odhlášení")) {
            log.info("Logout successful")
            loggedIn = false
            timer.cancel()
        } else {
            throw RuntimeException("Failed to logout!")
        }
    }

    @Synchronized
    fun join(room: Room) {
        log.info("Entering the room: ${room.name}")
        service.join(room)

        log.debug("Getting user list for room: ${room.name}")
        service.getRoomUsers(room)?.forEach(room::addUser)

        log.debug("Getting admin list for room: ${room.name}")
        room.admins = service.getRoomAdmins(room)?.map { it.nick } ?: emptyList()

        rooms.add(room)
        usersCheck()
    }

    @Synchronized
    fun part(room: Room) {
        log.info("Leaving the room: ${room.name}")
        val html = service.part(room)
        if (isLoggedPage(html)) {
            removeRoom(room)
        } else {
            throw RuntimeException("Failed to leave the room ${room.name}")
        }
    }

    fun say(room: Room, text: String, toUser: String? = null) {

        val msg = toUser?.let { user -> "/w $user $text" } ?: text

        log.debug("[${room.roomId},$toUser] Sending: $msg")
        val resp = service.say(room, msg)

        resp?.data?.let { roomData ->
            room.timestamp = System.currentTimeMillis()
            room.lastMessage = text

            if (room.chatIndex == roomData.index) {
                // Room's chat_index should be always different!
                throw RuntimeException("Your message probably wasn't sent! If you are an anonymous user, you can send only one message per 10 seconds!")
            } else {
                // Process new messages, if there are any in the response
                processRoomMessages(room, resp)
            }
        }
    }

    fun updateRoomInfo(room: Room) {
        service.getRoomInfo(room)?.let { info ->
            room.description = info.description ?: ""
            room.operatorId = info.adminUserId ?: -1
        }

        service.getRoomUsers(room)?.let { users ->
            users.forEach { user ->
                if (!room.hasUser(user)) {
                    room.addUser(user)
                }
            }
        }

        room.admins = service.getRoomAdmins(room)?.map { it.nick } ?: emptyList()
    }
}


