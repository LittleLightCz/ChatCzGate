import logging
from enum import Enum
from threading import Lock

log = logging.getLogger('chat')

class Room:
    """
    Data holder class for room information
    """
    def __init__(self, name, description, users_count):
        # Let's have ID as a string for the ease of further manipulation
        self.id = "-1"
        self.name = name
        self.description = description
        self.users_count = users_count
        self.user_list = []
        self.chat_index = ""

        self.lock = Lock()

    def __str__(self):
        return self.name

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



class User:
    """
    Data holder class for user
    """
    def __init__(self):
        self.id = -1
        self.name = ""
        self.gender = Gender.MALE

    def __init__(self, data):
        """
        Constructor from JSON data found in the room page right after entrance
        :param data: JSON data object
        """
        self.id = int(data["id"])
        self.name = data["nick"]
        self.gender = Gender(data["sex"])
        self.anonymous = bool(data["anonymous"])
        self.idle = int(data["interval_idle"]) if "interval_idle" in data else 0
        self.admin = int(data["roomAdmin"]) if "roomAdmin" in data else 0
        self.karma = data["karmaLevel"]

    def __str__(self):
        return self.name


class Gender(Enum):
    """Enum for gender"""
    MALE = "m"
    FEMALE = "f"

