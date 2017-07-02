package com.svetylkovo.chatczgate.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.svetylkovo.chatczgate.beans.*
import com.svetylkovo.chatczgate.cache.UsersCache
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.rest.ChatClient
import com.svetylkovo.chatczgate.service.ChatService
import okhttp3.ResponseBody
import org.apache.commons.lang3.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    fun getUserByName(name: String): User? {
        return rooms.map { it.getUserByName(name) }
                    .filterNotNull()
                    .firstOrNull()
                ?: UsersCache.getByName(name)
    }

    @Synchronized
    fun getRoomByName(name: String): Room? = getRooms().find { it.name == name }

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

        if (isLoggedPage(html)) {
            log.info("Login successful!")
            loggedIn = true
        } else if (alert != null) {
            throw RuntimeException(alert.text().trim())
        } else throw RuntimeException("Failed to login for unknown reason.")
    }

    private fun isLoggedPage(html: Document): Boolean {
        return html.select("li#nav-user").first() != null
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
        rooms.firstOrNull()?.let {
            say(it, text, toNick)
        }
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








/*

CHAT_CZ_URL = "https://chat.cz"
LOGIN_URL = CHAT_CZ_URL + "/login"
LOGOUT_URL = CHAT_CZ_URL + "/logout"
LEAVE_ROOM_URL = CHAT_CZ_URL + "/leaveRoom/%d"
PROFILE_URL = CHAT_CZ_URL + "/p/"

JSON_HEADER_URL = CHAT_CZ_URL + "/json/getHeader"
JSON_TEXT_URL = CHAT_CZ_URL + "/json/getText"
JSON_ROOM_USER_TIME_URL = CHAT_CZ_URL + "/json/getRoomUserTime"
JSON_ROOM_INFO_URL = CHAT_CZ_URL + "/api/room/"
JSON_ROOM_ADMIN_LIST_URL = CHAT_CZ_URL + "/api/room/%d/admins"
JSON_ROOM_USER_LIST_URL = CHAT_CZ_URL + "/api/room/%d/users"
JSON_ROOMS_LIST_URL = CHAT_CZ_URL + "/api/rooms"
JSON_USER_LOOKUP_URL = CHAT_CZ_URL + "/api/user/%d"
JSON_USER_PROFILE_URL = CHAT_CZ_URL + "/api/user/%d/profile"












    def logout(self):
        """
        Logs out the user
        """
        resp = req.get(LOGOUT_URL, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        if "Úspěšné odhlášení" in resp.text:
            self.logged = False
            log.info("Logout successful!")
        else:
            raise LogoutError("Logout failed!")

        log.debug("Removing scheduler events ...")
        for job in self._scheduler_jobs:
            schedule.cancel_job(job)

    def join(self, room):
        """
        Enters the room
        :param room: Room
        """
        log.info("Entering the room: "+room.nick)
        resp = req.get(CHAT_CZ_URL + "/" + room.nick, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        with room.lock:
            # Get users in the room
            log.debug("Getting user list for room: {0}".format(room.nick))
            resp = req.get(JSON_ROOM_USER_LIST_URL % room.uid)
            room.user_list = [User(user) for user in resp.json()["users"]]

            # Get admin list (not mandatory)
            log.debug("Getting admin list for room: {0}".format(room.nick))
            resp = req.get(JSON_ROOM_ADMIN_LIST_URL % room.uid)
            room.admin_list = [user["nick"] for user in resp.json()["admins"]]

        # Add room to the list
        with self._room_list_lock:
            self._room_list.append(room)

        # Trigger users check
        self._users_check()

    def part(self, room):
        """
        Leaves the room
        :param room: Room
        """
        log.info("Leaving the room: "+room.nick)
        resp = req.get(LEAVE_ROOM_URL % room.uid, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        html = BeautifulSoup(resp.text, "html.parser")
        with self._room_list_lock:
            if self._is_logged_page(html):
                # Remove room from the list
                self._remove_room(room)
            else:
                raise RoomError("Failed to leave the room: "+room.nick)



    def say(self, room, text, to_user=None):
        """
        Sends text to the room

        :param room: Room
        :param text: string
            Text to be told
        :param to_user: string
            Username to whisper to. Leave as None when talking to all.
        """
        if to_user:
            # Whispering
            text = '/w "{0}" {1}'.format(to_user, text)

        # Find our stored room in the list, or leave it as is
        with self._room_list_lock:
            # Create data
            data = {
                "roomId": room.uid,
                "chatIndex": room.chat_index,
                "text": text,
                "userIdTo": "0"
            }

            log.debug("[{0},{1}] Sending: {2}".format(room.uid, to_user, text))
            resp = req.post(JSON_TEXT_URL, headers=self._headers, data=data, cookies=self._cookies)
            self._cookies.update(resp.cookies)

            # Update room's timestamp
            room.timestamp = time.time()
            room.last_message = text

            # Server JSON response
            json_data = resp.json()

            if room.chat_index == json_data["data"]["index"]:
                # Room's chat_index should be always different!
                raise MessageError("Your message probably wasn't sent! If you are an anonymous user, you can send only one message per 10 seconds!")
            else:
                # Process new messages, if any from the response
                self._process_room_messages_from_json(json_data, room)

    def _update_room_info(self, room):
        """
        Updates room info
        :param room: Room
        """
        json = req.get(JSON_ROOM_INFO_URL+str(room.uid)).json()
        room.description = json["room"]["description"]
        room.operator_id = json["room"]["adminUserId"]











 */
}


