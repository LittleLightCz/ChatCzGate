from enum import Enum
from threading import Lock

import time

from logger import log


class Room:
    """
    Data holder class for room information
    """
    def __init__(self, id, name, description, users_count):
        # Let's have ID as a string for the ease of further manipulation
        self.id = id
        self.name = name
        self.description = description
        self.users_count = users_count
        self.user_list = []
        self.operator_id = "-1"
        self.admin_list = []
        self.chat_index = ""

        # Setup for idler
        self.last_message = ""
        self.timestamp = time.time()

        self.lock = Lock()

    def __str__(self):
        return self.name

    def has_user(self, user):
        """
        :param user: User
        :return True if this room already contains user with the same nickname
        """
        for u in self.user_list:
            if u.name == user.name:
                return True

        return False

    def add_user(self, user):
        """
        Adds user to the user_list
        :param user: User
        """
        self.user_list.append(user)

    def remove_user(self, user):
        """
        Removes user from the user_list
        :param user: User
        """
        u = self.get_user_by_id(user.id)
        if u:
            self.user_list.remove(u)
        else:
            log.error("Failed to remove the user: "+user.name)

    def get_user_by_id(self, id):
        return next((u for u in self.user_list if u.id == id), None)

    def __str__(self):
        return self.name


class User:
    """
    Data holder class for user
    """
    def __init__(self, data):
        """
        Constructor from JSON data found in the room page right after entrance
        :param data: JSON data object
        """
        self.id = int(data.get("id") or data.get("uid") or -1)
        self.name = data.get("nick", "")
        self.gender = Gender(data.get("sex", "m"))
        self.anonymous = bool(data.get("anonymous", True))
        self.idle = int(data.get("interval_idle", 0))
        self.admin = int(data.get("roomAdmin", 0))
        self.karma = data.get("karmaLevel", "")

    def __str__(self):
        return self.name


class Gender(Enum):
    """Enum for gender"""
    MALE = "m"
    FEMALE = "f"
