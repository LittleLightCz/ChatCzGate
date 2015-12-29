from enum import Enum

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

    def __str__(self):
        return self.name


class User():
    """
    Data holder class for user
    """
    def __init__(self):
        self.id = "-1"
        self.name = ""
        self.gender = Gender.MALE

    def __init__(self, data):
        """
        Constructor from JSON data found in the room page right after entrance

        Parameters:
            data : JSON data object
        """
        self.id = data["id"]
        self.name = data["nick"]
        self.gender = Gender.MALE if data["sex"] == "m" else Gender.FEMALE
        self.anonymous = bool(data["anonymous"])
        self.idle = int(data["interval_idle"])
        self.admin = int(data["roomAdmin"])
        self.karma = int(data["karmaLevel"])

    def __str__(self):
        return self.name


class Gender(Enum):
    """Enum for gender"""
    MALE = "m"
    FEMALE = "f"