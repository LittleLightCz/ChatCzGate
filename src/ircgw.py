import logging
import re
import socketserver
import sys

from chatapi import ChatAPI, ChatEvent
from error import LoginError

LINE_BREAK = "\r\n"
ENCODING = "UTF-8"

IRC_HOSTNAME = 'localhost'
IRC_LISTEN_PORT = 32132  # TODO get from config file
UNICODE_SPACE = u'\xa0'

VERSION = "0.1"

log = logging.getLogger("chat")


class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """

    def __init__(self, request, client_address, server):
        self.chatapi = ChatAPI(self)
        self.nickname = None
        self.username = None
        self.password = None
        self.hostname = IRC_HOSTNAME
        super().__init__(request, client_address, server)

    def handle(self):
        """ Handles incoming message """
        while True:  # TODO exit
            data = self.request.recv(512).decode('utf-8')  # TODO readline ?
            log.debug("RAW: %s" % data)
            lines = data.split(LINE_BREAK)
            for line in lines:
                self.parse_line(line)

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        match = re.match(r"(^\w+)\s*(.+)?", line)
        if match:
            command, args = match.groups()
            args = args or ""

            debug_args = re.sub(".", "*", args) if command == "PASS" else args
            log.debug("Parsed command %s args: %s " % (command, debug_args))
            self.handle_command(command, args)

    def reply(self, response_number, message):
        """ Send response to client """
        # TODO get server name
        response = ":%s %03d %s %s %s" % (self.hostname, response_number, self.nickname, message, LINE_BREAK)
        log.debug("Sending: %s" % response)
        self.request.send(str.encode(response, encoding=ENCODING))

    def not_enough_arguments_reply(self, command_name):
        self.reply(461, "%s :Not enough parameters" % command_name)

    def send_MOTD_text(self, text):
        self.reply(372, ":- "+text)

    def send_welcome_message(self):
        self.reply(1, ":Welcome to the Internet Relay Network %s!%s@%s" % (self.nickname, self.username, self.hostname))
        self.reply(2, ":Your host is %s, running version %s" % (self.hostname, VERSION))
        self.reply(3, ":")
        self.reply(4, "")
        self.reply(375, "Message of the day -")
        self.send_MOTD_text("*** ChatCzGate version "+VERSION+" ***")
        # self.send_MOTD_text("With great power comes great responsibility ...")

        self.reply(376, (self.nickname or self.username) + " :End of MOTD command.")

    def handle_command(self, command, args):
        """ IRC command handlers """
        def user_handler():
            arguments = args.split(' ')
            if len(arguments) == 4:
                self.username = arguments[0]
                #self.send_welcome_message()
            else:
                self.not_enough_arguments_reply(command)

        def nick_handler():
            self.nickname = args

            # When NICK is received, perform login
            try:
                if self.password:
                    self.chatapi.login(self.nickname, self.password)
                else:
                    # Todo Solve male/female problem
                    self.chatapi.login_as_anonymous(self.nickname)

                self.send_welcome_message()
            except LoginError as e:
                log.error(str(e))
                # Todo: send wrong password reply

        def pass_handler():
            if args:
                self.password = args[1:] if args[0] == ":" else args

        def list_handler():
            arguments = args.split(' ') if args else []
            available_channels = self.chatapi.get_room_list()

            if len(arguments) == 2:  # TODO ask another server
                available_channels = []
            elif len(arguments) == 1:  # expecting comma separated channels, return their topics
                channels = arguments[0].split(',')
                available_channels = [ch for ch in available_channels if ch.name in channels]

            self.reply(321, "Channel :Users  Name")

            for channel in available_channels:
                self.reply(322, "#%s %d :%s" % (channel.name.replace(' ', UNICODE_SPACE), channel.users_count, channel.description))
            self.reply(323, ":End of /LIST")

        def join_handler():
            arguments = args.split(' ')
            rooms = arguments[0].split(',')
            keys = arguments[1].split(',') if len(arguments) > 1 else []
            for room in rooms:
                # Remove leading hash sign
                room = re.sub(r"^#", "", room)
                r = self.chatapi.get_room_by_name(room)
                if r:
                    log.info("Joining room : %s", r.name)
                    self.chatapi.join(r)
                    # TODO RPL_NOTOPIC
                    self.request.send(str.encode(":%s!%s@%s JOIN #%s%s" % (self.nickname, self.username, self.hostname, r.name, LINE_BREAK ), encoding=ENCODING))
                    self.reply(332, "#%s :%s" % (r.name, r.description))
                    users_in_room = ' '.join([x.name for x in r.user_list])
                    self.reply(353, "= #%s :%s %s" % (r.name, self.nickname, users_in_room))
                    self.reply(366, "#%s :End of /NAMES list." % r.name)
                else:
                    log.info("Failed to join : %s", room)
                    # TODO room not found

        def quit_handler():
            self.chatapi.logout()

        def ping_handler():
            log.debug(args)
            pong = "PONG %s :1 %s%s" % (args, self.hostname, LINE_BREAK)  # TODO check
            self.request.send(str.encode(pong, encoding=ENCODING))

        # Supported commands
        commands = {  # TODO other commands
            "USER": user_handler,
            "NICK": nick_handler,
            "PASS": pass_handler,
            "LIST": list_handler,
            "JOIN": join_handler,
            "QUIT": quit_handler,
            "PING": ping_handler
        }

        try:
            log.debug("requesting command : %s", command)
            # Execute command handler
            commands[command]()
        except KeyError:
            log.error("IRC command not found: %s", command)

    def new_message(self, room, user, text, whisper):
        super().new_message(room, user, text, whisper)  # Todo

    def user_joined(self, room, user):
        super().user_joined(room, user)  # Todo

    def user_left(self, room, user):
        super().user_left(room, user)  # Todo


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    """ Allows multiple clients """
    pass

"""
Main launch script
"""

t = ThreadedTCPServer((IRC_HOSTNAME, IRC_LISTEN_PORT), IRCServer)  # TODO hostname from config

try:
    log.info("*** ChatCzGate version {0} ***".format(VERSION))
    log.info("Listening on port: {0}".format(IRC_LISTEN_PORT))
    t.serve_forever()
except KeyboardInterrupt:
    sys.exit(0)
