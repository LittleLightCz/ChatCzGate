import configparser
import html
import json
import logging
import re
import threading
import time

import requests as req
import schedule
from bs4 import BeautifulSoup

import js
from tools import rotate
from error import *
from room import Room, User, Gender

CHAT_CZ_URL = "https://chat.cz"
LOGIN_URL = CHAT_CZ_URL + "/login"
LOGOUT_URL = CHAT_CZ_URL + "/logout"
LEAVE_ROOM_URL = CHAT_CZ_URL + "/leaveRoom/"
PROFILE_URL = CHAT_CZ_URL + "/p/"

JSON_HEADER_URL = CHAT_CZ_URL + "/json/getHeader"
JSON_TEXT_URL = CHAT_CZ_URL + "/json/getText"
JSON_ROOM_USER_TIME_URL = CHAT_CZ_URL + "/json/getRoomUserTime"

MESSAGES_CHECK_INTERVAL = 5
USERS_CHECK_INTERVAL = 50


config = configparser.ConfigParser()
config.read('config.ini')


# -------------------------------------------------------------------------------
# Temporary logger init ... will be moved to main script file afterwards ...

LOGGER_FORMAT = '%(asctime)s %(name)-12s %(levelname)-8s %(message)s'

# Init logger
log = logging.getLogger('chat')
log.setLevel(config.get("Global", "loglevel", fallback="INFO")) # or whatever
handler = logging.FileHandler('log.txt', 'a', 'utf-8') # or whatever
handler.setFormatter = logging.Formatter('%(name)s %(message)s') # or whatever
log.addHandler(handler)

# define a Handler which writes INFO messages or higher to the sys.stderr
console = logging.StreamHandler()
console.setLevel(logging.DEBUG)
# set a format which is simpler for console use
formatter = logging.Formatter(LOGGER_FORMAT)
# tell the handler to use this format
console.setFormatter(formatter)
# add the handler to the root logger

log.addHandler(console)

# -------------------------------------------------------------------------------


class ChatEvent:
    """
    Class that defines chat events. Extend this class and overwrite it's methods with your implementation.
    """

    def new_message(self, room, user, text, whisper):
        """
        This method is called when there is new incoming message from the room
        :param room: Room
        :param user: User
        :param text: string
        :param whisper: bool
            True if the user is whispering to you
        """
        log.debug("<{0}> {1}: {2}".format(room.name, user.name, text))

    def user_joined(self, room, user):
        """
        This method is called when user joins the room
        :param room: Room
        :param user: User
        """
        log.debug("{1} joined {0}".format(room.name, user.name))

    def user_left(self, room, user):
        """
        This method is called when user lefts the room
        :param room: Room
        :param user: User
        """
        log.debug("{1} left {0}".format(room.name, user.name))

    def system_message(self, room, message):
        """
        This method is called when there is new system message
        :param room: Room
        :param message: string
        """
        log.debug("<{0}> SYSTEM: {1}".format(room.name, message))

    def user_mode(self, room, user, mode):
        """
        This method is called when there is new user mode change request
        :param room: Room
        :param user: User
        :param mode: string
        """
        log.debug("<{0}> {1} => Mode {2}".format(room.name, user.name, mode))

    def kicked(self, room):
        """
        This method is called when you have been kicked from the room
        :param room: Room
        """
        log.debug("<{0}> => You have been kicked@!".format(room.name))


class ChatAPI:
    """
    Class that enables access to chat.cz
    """

    def __init__(self, event_handler=ChatEvent()):
        """
        Constructor
        :param event_handler: ChatEvent
        """
        self._event = event_handler

        self.logged = False

        log.info("Getting cookies ...")
        self._cookies = req.get(LOGIN_URL).cookies

        self._headers = {
            "User-Agent":"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36"
        }
        log.debug(self._headers)

        # Set up internal room list
        self._room_list = []
        self._room_list_lock = threading.Lock()

        # Set up schedule
        self._scheduler_jobs = []
        self._scheduler_jobs.append(schedule.every(USERS_CHECK_INTERVAL).seconds.do(self._users_check))
        self._scheduler_jobs.append(schedule.every(MESSAGES_CHECK_INTERVAL).seconds.do(self._messages_check))

        # Setup idler
        self.idler_enabled = config.getboolean("Idler", "enabled", fallback=False)
        self.idle_time = config.getint("Idler", "idle_seconds", fallback=1800)
        self.idle_strings = config.get("Idler", "idle_strings", fallback=".,..").split(",")

    def _run_schedule_continuously(self):
        while self.logged:
            schedule.run_pending()
            time.sleep(1)

    def _users_check(self):
        """
        Private method that updates user idle times. It is scheduled to run every USERS_CHECK_INTERVAL seconds.
        """
        try:
            # Get header
            resp = req.post(JSON_HEADER_URL, headers=self._headers, cookies=self._cookies)

            # Get room's user info
            with self._room_list_lock:
                for room in self._room_list:
                    data = {"roomId": room.id}
                    resp = req.post(JSON_ROOM_USER_TIME_URL, headers=self._headers, data=data, cookies=self._cookies)
        except:
            log.exception("Error during users' info check!")

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
                    # Add user to DB
                    UserDb.add_user(user)
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
            user = room.get_user_by_id(uid) or UserDb.get_user_by_uid(uid)
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

    def _messages_check(self):
        """
        Checks for new messages and triggers appropriate event
        :param room: Room
        """
        try:
            idler_rooms = []

            with self._room_list_lock:
                idler_rooms = list(self._room_list)
                for room in self._room_list:
                    data = {
                        "roomId": room.id,
                        "chatIndex": room.chat_index,
                    }

                    log.debug("Checking for new messages in: "+room.name)
                    resp = req.post(JSON_TEXT_URL, headers=self._headers, data=data, cookies=self._cookies)
                    self._cookies.update(resp.cookies)

                    json_data = resp.json()
                    self._process_room_messages_from_json(json_data, room)

            for room in idler_rooms:
                # Trigger idler
                self._idler_trigger(room)
        except:
            log.exception("Error during new messages check!")

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

    def get_user_by_name(self, nick):
        """
        Searches all rooms for a User by its nickname. If this search fails, it will try to search
        in shared UserDb.
        :param nick: string
            User's nickname
        :return: User instance or None if no User was found
        """
        # First search all active rooms
        with self._room_list_lock:
            for room in self._room_list:
                for user in room.user_list:
                    if user.name == nick:
                        return user

        # If previous search failed, search the UserDb
        return UserDb.get_user_by_name(nick)

    def get_room_by_name(self, name):
        """
        Downloads full room list from server and returns room instance by its name
        :param name: string
        :return: Room instance
        """
        return next((r for r in self.get_room_list() if r.name == name), None)

    def get_active_room_names(self):
        """
        Returns list of active room names
        :return: List of strings
        """
        with self._room_list_lock:
            return [r.name for r in self._room_list]

    def get_active_room_by_name(self, name):
        """
        Returns room instance from internal _room_list by its name
        :param name: string
        :return: Room instance
        """
        return next((r for r in self._room_list if r.name == name), None)

    def get_room_list(self):
        """
        Returns the list of all rooms on the server sorted by name. Data are held in the class Room.
        """
        log.info("Downloading room list ...")
        resp = req.get(CHAT_CZ_URL)
        html = BeautifulSoup(resp.text, "html.parser")

        def to_room(div):
            name = div.a.h4.text.strip()
            description = re.sub(r"[\s\S]+?\n\n", "", div.a.text).strip()
            users_count = len(div.div.find_all("span"))
            return Room(name, description, users_count)

        divs = html.find_all("div", "row row-xs-height list-group")
        rooms = [to_room(div) for div in divs]
        rooms.sort(key=lambda r: r.name)

        # TEMPORARY, enrich rooms by their IDs -- DELETE after Chat api returns rooms also with users count
        json_rooms = req.get("https://chat.cz/api/rooms").json()
        for r in rooms:
            for r_json in json_rooms["rooms"]:
                if r.name == r_json["name"]:
                    r.id = r_json["id"]
                    break

        log.debug([r.name for r in rooms])
        return rooms

    def login(self, user, password):
        """
        Logs in to the server as an anonymous user.

        :param user: string
            Username or email
        :param password: string
            Password
        """
        data = {"email": user, "password": password}

        log.info("Logging as: {0}, password {1}".format(user, re.sub(".", "*", password)))
        resp = req.post(LOGIN_URL, headers=self._headers, data=data, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        # Check whether the login was successful
        self._login_check(resp)

    def login_as_anonymous(self, user, gender=Gender.MALE):
        """
        Logs in to the server as an anonymous user.

        :param user: string
            Username
        :param gender: Gender
            Gender from Gender enum. Default is MALE.
        """
        data = {"nick": user, "sex": gender.value}

        log.info("Logging anonymously as: {0} ({1})".format(user, gender.value))
        resp = req.post(LOGIN_URL, headers=self._headers, data=data, cookies=self._cookies)
        self._cookies.update(resp.cookies)

        # Check whether the login was successful
        self._login_check(resp)

    def _login_check(self, resp):
        html = BeautifulSoup(resp.text, "html.parser")
        alert = html.find("div", {"class": "alert"})

        self.logged = False

        if self._is_logged_page(html):
            self.logged = True
        elif alert:
            match = re.search(r"\n.+?\n.*$", alert.text)
            raise LoginError(match.group().strip())
        else:
            raise LoginError("Failed to login for unknown reason.")

        log.info("Login successful!")

        # Run schedule
        threading.Thread(target=self._run_schedule_continuously).start()

    def _is_logged_page(self, html):
        """
        Checks whether provided html correspond with the main chat page with logged user.

        :param html: BeautifulSoup Tag
        :return: Searched <li> tag or None
        """
        return html.find("li", {"id": "nav-user"})

    def _get_idler_messgae(self, last_message):
        """
        :param last_message: string
        :return: idler message that is not the same as last_message
        """
        self.idle_strings = rotate(self.idle_strings)
        if self.idle_strings[0] == last_message:
            self.idle_strings = rotate(self.idle_strings)

        return self.idle_strings[0]

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

        resp = req.get(PROFILE_URL+nick, headers=self._headers)

        html = BeautifulSoup(resp.text, "html.parser")

        div = html.find("div", {"id": "userInfo"})

        if div:
            profile = UserProfile()
            profile.online = bool(div.div.h2.find("i", {"title": "online"}))

            if div.div.h2.span:
                profile.karma = div.div.h2.span.find("i", {"class": "fa-stack-1x"}).text

            img_profile = div.find("img", {"title": "Profil"})
            if img_profile:
                profile.profile_image = CHAT_CZ_URL+img_profile["src"]

            if profile.karma:
                profile.nick = re.sub(r"\d+\s*$","",div.div.h2.text).strip()
            else:
                profile.nick = div.div.h2.text.strip()

            text = div.div.text
            m = next(re.finditer(r"věk:\s*(.+)", text), None)
            if m:
                profile.age = m.group(1).strip()

            m = next(re.finditer(r"registrace:\s*(.+)", text), None)
            if m:
                profile.registration = m.group(1).strip()

            m = next(re.finditer(r"naposledy:\s*(.+)", text), None)
            if m:
                profile.last_seen = m.group(1).strip()

            m = next(re.finditer(r"profil zobrazen:\s*(.+)", text), None)
            if m:
                profile.viewed = m.group(1).strip()

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
            id_match = re.search(r"/leaveRoom/(\d+)", resp.text)
            if id_match:
                room.id = id_match.group(1)
                log.info("Room ID is: "+room.id)
            else:
                raise RoomError("Failed to get room ID!")

            # Get users in the room
            match = re.search(r"var userList\s*=\s*({[\s\S]+?});", resp.text)
            if match:
                data = js.to_py_json(match.group(1))["data"]
                room.user_list = [User(val) for key,val in data.items()]
                UserDb.add_users(room.user_list)
            else:
                raise RoomError("Failed to get user list for the room: "+room)

            # Get admin list (not mandatory)
            match = re.search(r"adminList:\s*(\{.*?\})", resp.text)
            if match:
                data = js.to_py_json(match.group(1))
                room.admin_list = [val for key,val in data.items()]
            else:
                log.warning("Failed to parse Admin list of the room: "+room.name)

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
        resp = req.get(LEAVE_ROOM_URL + room.id, headers=self._headers, cookies=self._cookies)
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

            # Server JSON response
            json_data = resp.json()

            if room.chat_index == json_data["data"]["index"]:
                # Room's chat_index should be always different!
                raise MessageError("Your message probably wasn't sent! If you are an anonymous user, you can send only one message per 10 seconds!")
            else:
                # Process new messages, if any from the response
                self._process_room_messages_from_json(json_data, room)


class UserProfile:
    """
    Data holder class form user's profile
    """

    def __init__(self):
        self.online = False
        self.karma = ""
        self.nick = ""
        self.age = ""
        self.registration = ""
        self.last_seen = ""
        self.viewed = ""
        self.profile_image = None


class UserDb:
    """
    Static class used to store all User instances for later search by ID or name
    """

    _lock = threading.Lock()
    _users = []

    @classmethod
    def get_user_by_name(cls, nick):
        """
        Find user by name
        :param nick: string
        :return: User or None
        """
        with cls._lock:
            return next((u for u in cls._users if u.name == nick), None)

    @classmethod
    def get_user_by_uid(cls, uid):
        """
        Find user by UID
        :param uid: int
        :return: User or None
        """
        with cls._lock:
            return next((u for u in cls._users if u.id == uid), None)

    @classmethod
    def add_user(cls, user):
        """
        Adds user to the list, if it's not already there
        :param user: User
        """
        u = cls.get_user_by_name(user.name)
        with cls._lock:
            if not u:
                log.debug("UserDb: Adding user {0} ({1})".format(user.name, user.id))
                cls._users.append(user)

    @classmethod
    def add_user_from_json(cls, json):
        """
        Adds user from JSON data to the list
        :param json: JSON object
        """
        u = User(json)
        cls.add_user(u)

    @classmethod
    def add_users(cls, users):
        """
        Adds multiple users
        :param users: list of User
        """
        for user in users:
            cls.add_user(user)

