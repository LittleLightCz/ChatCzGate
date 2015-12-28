import logging as log
from enum import Enum

import requests as req
from bs4 import BeautifulSoup


CHAT_CZ_URL = "https://chat.cz"
LOGIN_URL = CHAT_CZ_URL + "/login"
LOGOUT_URL = CHAT_CZ_URL + "/logout"

JSON_HEADER_URL = CHAT_CZ_URL + "/json/getHeader"

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
    MALE = "m"
    FEMALE = "f"


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
        log.info("Getting cookie ...")
        self.cookies = req.get(LOGIN_URL).cookies

        self.headers = {
            "User-Agent" : "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36"
        }
        log.debug(self.headers)
        pass

    def get_room_list(self):
        """
        Returns the list of all rooms on the server sorted by name. Data are held in the class Room.
        """
        log.info("Downloading room list ...")
        resp = req.get(CHAT_CZ_URL)
        html = BeautifulSoup(resp.text, "html.parser")

        def to_room(div):
            name = div.a.h4.text.strip()
            description = div.a.text.replace(name,"",1).replace("[\n\r]","").strip()
            return Room(name, description)

        divs = html.find_all("div","row row-xs-height list-group")
        rooms = [ to_room(div) for div in divs ]
        rooms.sort(key = lambda r: r.name)

        log.debug([r.name for r in rooms])
        return rooms


    def login_as_anonymous(self,user,gender = Gender.MALE):
        """
        Logins to the server as an anonymous user.

        Parameters:
            user : string
                   Username
            gender : Gender
                     Gender from Gender enum. Default is MALE.
        """
        params = {"nick" : user, "sex" : gender.value}

        log.info("Logging anonymously as: {0} ({1})".format(user,gender.value))
        resp = req.post(LOGIN_URL, headers=self.headers, params=params, cookies=self.cookies)
        self.cookies.update(resp.cookies)

        print("nav-user" in resp.text)


