package com.svetylkovo.chatczgate.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.svetylkovo.chatczgate.beans.*
import com.svetylkovo.chatczgate.cache.UsersCache
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.service.ChatService
import org.apache.commons.lang3.StringEscapeUtils
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule

class ChatApi(val chatEvent: ChatEvent) {

    private val MESSAGES_CHECK_INTERVAL: Long = 5*1000
    private val USERS_CHECK_INTERVAL: Long = 50*1000

    private val log: Logger = LoggerFactory.getLogger(ChatApi::class.java)

    private val service = ChatService()
    private val rooms = ArrayList<Room>()

    private val timer = Timer()
    private val mapper = ObjectMapper()

    private var loggedIn = false
    private var idlerEnabled = false
    private var idleTime = 1800
    private var idleStrings = listOf(".", "..")

    init {
        log.info("Getting cookies ...")
        service.pingLoginPage()

        timer.schedule(0, USERS_CHECK_INTERVAL) {
            usersCheck()
        }

        timer.schedule(0, MESSAGES_CHECK_INTERVAL) {
            messagesCheck()
        }
    }

    @Synchronized
    private fun usersCheck() {
        try {
            if (loggedIn) {
                service.pingHeader()
                rooms.forEach { service.pingRoomUserTime(it) }
            }
        } catch (t: Throwable) {
            log.error("Error during pinging users' room time!", t)
        }
    }

    @Synchronized
    private fun messagesCheck() {
        try {
            for(room in rooms) {
                log.debug("Checking for new messages in: ${room.name}")
                val response = service.getRoomText(room)
                processRoomMessages(room, response)
                triggerIdler(room)
            }
        } catch (t: Throwable) {
            log.error("Error during new messages check!", t)
        }
    }

    @Synchronized
    fun getUserByName(name: String) =
            rooms.map { it.getUserByName(name) }
                    .filterNotNull()
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
        return service.getRoomList()?.sortedBy { it.name } ?: emptyList()
    }

    fun login(user: String, password: String) {
        log.info("Logging as: $user")
        val response = service.login(Login(user, password))
        loginCheck(response)
    }

    fun loginAnonymously(user: String, gender: Gender) {
        log.info("Logging anonymously as: $user (${gender.value})")
        val response = service.loginAnonymously(AnonymousLogin(user, gender.value))
        loginCheck(response)
    }

    private fun loginCheck(resp: String) {
        val html = Jsoup.parse(resp)
        val alert = html.select("div[class=alert]")
                        .firstOrNull()

        loggedIn = false

        if (isLoggedPage(resp)) {
            log.info("Login successful!")
            loggedIn = true
        } else if (alert != null) {
            throw RuntimeException(alert.text().trim())
        } else throw RuntimeException("Failed to login for unknown reason.")
    }

    private fun isLoggedPage(html: String): Boolean {
        return Jsoup.parse(html)
                .select("li#nav-user")
                .first() != null
    }

    private fun triggerIdler(room: Room) {
    }

    private fun getIdlerMessage(lastMessage: String): String {
        val idleString = idleStrings.filter { it != lastMessage }
                                    .firstOrNull() ?: "..."

        Collections.rotate(idleStrings, 1)
        return idleString
    }

    private fun idlerTrigger(room: Room) {
        if (idlerEnabled && System.currentTimeMillis() - room.timestamp > idleTime) {
            val msg = getIdlerMessage(room.lastMessage)
            say(room, msg)
            chatEvent.systemMessage(room, "IDLER: $msg")
        }
    }

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
    private fun  processRoomMessages(room: Room, resp: RoomResponse?) {
        if (resp?.success != null) {
            resp.data?.index?.let { room.chatIndex = it }
            resp.data?.data?.forEach { processMessage(room, it)}
        } else {
            when(resp?.statusMessage) {
                "User in room NOT_FOUND" -> {
                    chatEvent.kicked(room)
                    removeRoom(room)
                }
                else -> {
                    log.error("Failed to get new messages: ${resp?.statusMessage}")
                }
            }
        }
    }

    @Synchronized
    private fun processMessage(room: Room, message: RoomMessage) {
        when(message.s){
            "enter" -> {
                val user = message.user
                UsersCache.addUser(user)
                room.addUser(user)
                chatEvent.userJoined(room, user)
            }
            "leave","auto_leave" -> {
                room.getUserByUid(message.uid)?.let { user ->
                    room.removeUser(user)
                    chatEvent.userLeft(room, user)
                }
            }
            "cli" -> chatEvent.systemMessage(room, message.t)
            "user","friend" -> UsersCache.addUser(message.user)
            "admin" -> {
                UsersCache.getByName(message.nick)?.let { user ->
                    updateRoomInfo(room)
                    if (room.operatorId == user.uid)
                        chatEvent.userMode(room, user, "+h")
                }
            }
            is String -> log.warn("Unknown system message: \n${mapper.writeValueAsString(message)}")
            else -> {
                //Standard message
                val user = UsersCache.getByUid(message.uid)

                if (user != null) {
                    val text = StringEscapeUtils.unescapeHtml4(message.t)

                    if (message.w != null) {
                        if (message.to == 0 && room == rooms.first()) {
                            chatEvent.newMessage(room, user, text, true)
                        }
                    } else {
                        chatEvent.newMessage(room, user, text, false)
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
        room.admins = service.getRoomAdmins(room) ?: emptyList()

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

        log.debug("[${room.roomId},$toUser] Sending: ${msg}")
        service.say(room, msg)

        room.timestamp = System.currentTimeMillis()
        room.lastMessage = text

        //TODO debug this:
//        if room.chat_index == json_data["data"]["index"]:
//        # Room's chat_index should be always different!
//        raise MessageError("Your message probably wasn't sent! If you are an anonymous user, you can send only one message per 10 seconds!")
//        else:
//        # Process new messages, if any from the response
//        self._process_room_messages_from_json(json_data, room)
    }

    fun updateRoomInfo(room: Room) {
        service.getRoomInfo(room)?.let {info ->
            room.description = info.description ?: ""
            room.operatorId = info.operatorId ?: -1
        }
    }
}


