import logging as log
import requests as req
import re, schedule
import threading

import time
from bs4 import BeautifulSoup

import js
from error import *
from room import Room, User, Gender

CHAT_CZ_URL = "https://chat.cz"
LOGIN_URL = CHAT_CZ_URL + "/login"
LOGOUT_URL = CHAT_CZ_URL + "/logout"

JSON_HEADER_URL = CHAT_CZ_URL + "/json/getHeader"
JSON_TEXT_URL = CHAT_CZ_URL + "/json/getText"
JSON_ROOM_USER_TIME_URL = CHAT_CZ_URL + "/json/getRoomUserTime"

MESSAGES_CHECK_INTERVAL = 5
USERS_CHECK_INTERVAL = 50

#-------------------------------------------------------------------------------
#Temporary logger init ... will be moved to main script file afterwards ...

LOGGER_FORMAT = '%(asctime)s %(name)-12s %(levelname)-8s %(message)s'

#Init logger
log.basicConfig(level=log.DEBUG,
                format=LOGGER_FORMAT,
                filename='log.txt',
                filemode='a')
# define a Handler which writes INFO messages or higher to the sys.stderr
console = log.StreamHandler()
console.setLevel(log.DEBUG)
# set a format which is simpler for console use
formatter = log.Formatter(LOGGER_FORMAT)
# tell the handler to use this format
console.setFormatter(formatter)
# add the handler to the root logger
log.getLogger('').addHandler(console)

#-------------------------------------------------------------------------------


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
        pass

    def user_joined(self, room, user):
        """
        This method is called when user joins the room
        :param room: Room
        :param user: User
        """
        log.debug("{1} joined {0}".format(room.name, user.name))
        pass

    def user_left(self, room, user):
        """
        This method is called when user lefts the room
        :param room: Room
        :param user: User
        """
        log.debug("{1} left {0}".format(room.name, user.name))
        pass


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
        self._event_lock = threading.Lock()

        self.logged = False

        log.info("Getting cookies ...")
        self._cookies = req.get(LOGIN_URL).cookies

        self._headers = {
            "User-Agent":"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36"
        }
        log.debug(self._headers)

        # Set up internal room list
        self._room_list = []

        # Set up schedule
        self._scheduler_jobs = []
        self._scheduler_jobs.append(schedule.every(USERS_CHECK_INTERVAL).seconds.do(self._users_check))
        self._scheduler_jobs.append(schedule.every(MESSAGES_CHECK_INTERVAL).seconds.do(self._messages_check))

    def _run_schedule_continuously(self):
        while self.logged:
            schedule.run_pending()
            time.sleep(1)

    def _users_check(self):
        """
        Private method that updates user idle times. It is scheduled to run every USERS_CHECK_INTERVAL seconds.
        """

        # Get header
        resp = req.post(JSON_HEADER_URL, headers=self._headers, cookies=self._cookies)

        # Get room's user info
        for room in self._room_list:
            data = {"roomId": room.id}
            resp = req.post(JSON_ROOM_USER_TIME_URL, headers=self._headers, data=data, cookies=self._cookies)


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
                room.add_user(user)
                self._event.user_joined(room, user)
            elif msg["s"] in ["leave","auto_leave"]:
                user = room.get_user_by_id(msg["uid"])
                if user:
                    room.remove_user(user)
                    self._event.user_left(room, user)
        else:
            # Standard chat message
            user = room.get_user_by_id(msg["uid"])
            whisper = True if "w" in msg else False
            self._event.new_message(room, user, msg["t"], whisper)
            pass

    def _messages_check(self):
        """
        Checks for new messages and triggers appropriate event
        :param room: Room
        """
        for room in self._room_list:
            data = {
                "roomId": room.id,
                "chatIndex": room.chat_index,
            }

            log.debug("Checking for new messages in: "+room.name)
            resp = req.post(JSON_TEXT_URL, headers=self._headers, data=data, cookies=self._cookies)
            self._cookies.update(resp.cookies)

            json_data = resp.json()
            if json_data['success']:
                # Update chat index
                room.chat_index = json_data['data']['index']
                # Get messages
                messages = json_data['data']['data']
                for msg in messages:
                    self._process_message(room, msg)
            else:
                log.error("Failed to get new messages: "+json_data['statusMessage'])

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

        divs = html.find_all("div","row row-xs-height list-group")
        rooms = [to_room(div) for div in divs]
        rooms.sort(key=lambda r: r.name)

        log.debug([r.name for r in rooms])
        return rooms

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
        html = BeautifulSoup(resp.text, "html.parser")
        nav_user = html.find("li", {"id": "nav-user"})
        alert = html.find("div", {"class": "alert"})

        self.logged = False
        if nav_user:
            self.logged = True
        elif alert:
            match = re.search(r"\n.+?\n.*$",alert.text)
            raise LoginError(match.group().strip())
        else:
            raise LoginError("Failed to login for unknown reason.")

        log.info("Login successful!")

        # Run schedule
        threading.Thread(target=self._run_schedule_continuously).start()

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
        log.info("Entering ther room: "+room.name)
        resp = req.get(CHAT_CZ_URL +"/" + room.name, headers=self._headers, cookies=self._cookies)
        self._cookies.update(resp.cookies)

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
        else:
            raise RoomError("Failed to get user list for the room: "+room)

        # Add room to the list
        self._room_list.append(room)

        # Trigger users check
        self._users_check()

    def say(self, room, text, to_user=None):
        """
        Sends text to the room

        :param room: Room
        :param text: string
            Text to be told
        :param to_user: string
            Username to whisper to. Leave as None when talking to all.
        """
        data = {
            "roomId": room.id,
            "chatIndex": "14|4",
            "text": text,
            "userIdTo": "0"
        }

        #Here check for user's ID and update userIdTo
        #...... TODO

        log.debug("[{0},{1}] Sending: {2}".format(room.id, to_user, text))
        resp = req.post(JSON_TEXT_URL, headers=self._headers, data=data, cookies=self._cookies)
        self._cookies.update(resp.cookies)


