package com.svetylkovo.chatczgate.api

import com.svetylkovo.chatczgate.beans.*
import com.svetylkovo.chatczgate.cache.UsersCache
import com.svetylkovo.chatczgate.events.ChatEvent
import com.svetylkovo.chatczgate.service.ChatService
import com.svetylkovo.rojo.Rojo
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule

class ChatApi(val handler: ChatEvent) {

    private val MESSAGES_CHECK_INTERVAL: Long = 5*1000
    private val USERS_CHECK_INTERVAL: Long = 50*1000

    private val log: Logger = LoggerFactory.getLogger(ChatApi::class.java)

    private val service = ChatService.obtain()
    private val rooms = ArrayList<Room>()

    private val timer = Timer()

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
                processMessages(response)
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
        return service.getRoomList().sortedBy { it.name }
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

    private fun loginCheck(resp: ResponseBody) {
        val html = Jsoup.parse(resp.string())
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

    private fun  triggerIdler(room: Room) {
    }

    private fun processMessages(response: String) {

    }

    private fun getIdlerMessage(lastMessage: String) =
            idleStrings.filter { it != lastMessage }.firstOrNull()
            ?: "..."


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



    def _process_message(self, room, msg):
        """
        Parse and process JSON message data
        :param room: Room
        :param msg: JSON data object
        """
        if "s" in msg:
            # System message
            if msg["s"] == "enter":
                user = User(msg["user"])
                # Add user to DB
                UserDb.add_user(user)
                # Add user to the room if he already isn't there
                if not room.has_user(user):
                    room.add_user(user)
                    self._event.user_joined(room, user)
            elif msg["s"] in ["leave","auto_leave"]:
                user = room.get_user_by_id(msg["uid"])
                if user:
                    room.remove_user(user)
                    self._event.user_left(room, user)
            elif msg["s"] == "cli":
                # Send system notice
                self._event.system_message(room, msg["t"])
            elif msg["s"] == "user":
                # Info about whispering user ...
                UserDb.add_user_from_json(msg["user"])
            elif msg["s"] == "admin":
                # Send mode OP
                user = UserDb.get_user_by_name(msg["nick"])
                self._update_room_info(room)
                if room.operator_id == user.id:
                    self._event.user_mode(room, user, "+h")
            elif msg["s"] == "friend":
                # ?? just add him to DB :-)
                UserDb.add_user_from_json(msg)
            else:
                log.warning("Unknown system message:")
                log.warning(json.dumps(msg, indent=4))
        else:
            # Standard chat message
            uid = msg["uid"]
            user = UserDb.get_user_by_uid(uid)
            if user:
                # Ignore messages from users that are not in the room?
                whisper = "w" in msg
                text = html.unescape(msg["t"])
                if whisper:
                    # If to == 1, then ignore this whisper message because it comes from me
                    # and ignore all whisper messages from other rooms except the "first" one
                    if msg["to"] == 0 and room == self._room_list[0]:
                        self._event.new_message(room, user, text, whisper)
                else:
                    self._event.new_message(room, user, text, whisper)
            else:
                message = "Unknown UID: {0} -> {1}".format(uid, msg["t"])
                log.warning(message)
                self._event.system_message(room, "WARNING: "+message)


    def _process_room_messages_from_json(self, json_data, room):
        """
        Processes the new messages from JSON response for a specific room.

        WARNING: Always call this method with _room_list_lock

        :param json_data: JSON
        :param room: Room
        """
        with room.lock:
            if json_data['success']:
                # Update chat index
                room.chat_index = json_data['data']['index']
                # Get messages
                messages = json_data['data']['data']
                for msg in messages:
                    self._process_message(room, msg)
            else:
                error_message = json_data['statusMessage']
                if error_message == "User in room NOT_FOUND":
                    # You have been kicked from the room (automatically or intentionally?)
                    self._event.kicked(room)
                    self._remove_room(room)
                else:
                    log.error("Failed to get new messages: " + error_message)




    def _idler_trigger(self, room):
        """
        Triggers idler for given room.
        :param room: Room
        """
        if self.idler_enabled and time.time()-room.timestamp > self.idle_time:
            msg = self._get_idler_messgae(room.last_message)
            self.say(room, msg)
            self._event.system_message(room, "IDLER: {0}".format(msg))

    def get_user_profile(self, nick):
        """
        Return user's profile
        :param nick: string
        :return: UserProfile
        """

        user = self.get_user_by_name(nick)

        if user:
            profile = UserProfile()

            # User lookup data
            data = req.get(JSON_USER_LOOKUP_URL % user.id).json()
            if "user" in data:
                d = data["user"]
                profile.anonymous = d["anonym"]
                profile.nick = d["nick"]
                profile.online = d["online"]
                profile.gender = Gender(d["sex"])
                profile.profile_url = PROFILE_URL + urllib.parse.quote(profile.nick)

            if "rooms" in data:
                profile.rooms = [r["name"] for r in data["rooms"]]

            # User profile data
            data = req.get(JSON_USER_PROFILE_URL % user.id).json()
            if "profile" in data:
                p = data["profile"]
                profile.age = p["age"]
                profile.profile_image = p.get("imageUrl")
                profile.viewed = p["profileViewCount"]

            return profile
        else:
            return None

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
        log.info("Entering the room: "+room.name)
        resp = req.get(CHAT_CZ_URL + "/" + room.name, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        with room.lock:
            # Get users in the room
            log.debug("Getting user list for room: {0}".format(room.name))
            resp = req.get(JSON_ROOM_USER_LIST_URL % room.id)
            room.user_list = [User(user) for user in resp.json()["users"]]

            # Get admin list (not mandatory)
            log.debug("Getting admin list for room: {0}".format(room.name))
            resp = req.get(JSON_ROOM_ADMIN_LIST_URL % room.id)
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
        log.info("Leaving the room: "+room.name)
        resp = req.get(LEAVE_ROOM_URL % room.id, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        html = BeautifulSoup(resp.text, "html.parser")
        with self._room_list_lock:
            if self._is_logged_page(html):
                # Remove room from the list
                self._remove_room(room)
            else:
                raise RoomError("Failed to leave the room: "+room.name)

    def _remove_room(self, room):
        self._room_list = [r for r in self._room_list if r.id != room.id]

    def admin(self, room, nick):
        """
        Adds admin rights to nick
        :param room: Room
        :param nick: string
        """
        self.say(room, "/admin "+nick)

    def whisper(self, to_user, text):
        """
        Whispers to the user

        :param to_user: string
            Username to whisper to
        :param text: string
            Text to be told
        """
        if len(self._room_list) > 0:
            room = self._room_list[0]
            self.say(room, text, to_user)
        else:
            raise MessageError("Failed to create a whisper message! There are no active rooms. Join the room first!")

    def kick(self, room, user, reason):
        """
        Kicks user from the room

        :param room: Room
        :param user: string
        :param reason: string
        """
        self.say(room, "/kick {0} {1}".format(user, reason))

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
                "roomId": room.id,
                "chatIndex": room.chat_index,
                "text": text,
                "userIdTo": "0"
            }

            log.debug("[{0},{1}] Sending: {2}".format(room.id, to_user, text))
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
        json = req.get(JSON_ROOM_INFO_URL+str(room.id)).json()
        room.description = json["room"]["description"]
        room.operator_id = json["room"]["adminUserId"]











 */