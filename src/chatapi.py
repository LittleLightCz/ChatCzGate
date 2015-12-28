import logging as log
from enum import Enum

import requests as req
from bs4 import BeautifulSoup


CHAT_CZ_URL = "https://chat.cz"

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

class Gender(Enum):
    """Enum for gender"""
    MALE = 1
    FEMALE = 2


class Room:
    """
    Data holder class for room information
    """

    def __init__(self, name, description):
        self.name = name
        self.description = description

    def __str__(self):
        return self.name


class ChatAPI:
    """
    Class that enables access to chat.cz
    """

    def __init__(self):
        pass

    def get_room_list(self):
        """
        Returns the list of all rooms on the server sorted by name. Data are held in the class Room.
        """
        resp = req.get(CHAT_CZ_URL)
        html = BeautifulSoup(resp.text, "html.parser")

        def to_room(div):
            name = div.a.h4.text.strip()
            description = div.a.text.replace(name,"",1).replace("[\n\r]","").strip()
            return Room(name, description)

        divs = html.find_all("div","row row-xs-height list-group")
        rooms = [ to_room(div) for div in divs ]
        rooms.sort(key = lambda r: r.name)
        return rooms



