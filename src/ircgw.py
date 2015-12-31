import logging
import re
import socketserver
import sys

from chatapi import ChatAPI, ChatEvent
from error import LoginError

IRC_LISTEN_PORT = 32132  # TODO get from config file
UNICODE_SPACE = u'\xa0'

VERSION = "0.1"

log = logging.getLogger("chat")


class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """

    def __init__(self, request, client_address, server):
        self.chatapi = ChatAPI(self)
        self.nickname = ""
        self.password = None
        super().__init__(request, client_address, server)

    def handle(self):
        """ Handles incoming message """
        socket_file = self.request.makefile("r")
        while True:
            self.parse_line(socket_file.readline())

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        match = re.match(r"(^\w+)\s+(.+)", line)
        if match:
            command, args = match.groups()
            debug_args = re.sub(".", "*", args) if command == "PASS" else args
            log.debug("Parsed command %s args: %s " % (command, debug_args))
            self.handle_command(command, args)

    def reply(self, response_number, message):
        """ Send response to client """
        # TODO get server name
        response = ":%s %d %s %s%s" % ('localhost', response_number, self.nickname, message, "\r\n")
        log.debug("Sending: %s" % response)
        self.request.send(str.encode(response))

    def not_enough_arguments_reply(self, command_name):
        self.reply(461, "%s :Not enough parameters" % command_name)

    def handle_command(self, command, args):
        """ IRC command handlers """
        def user_handler():
            arguments = args.split(' ')
            if len(arguments) == 4:
                self.reply(1, "Welcome to ChatCzGate!")
                self.reply(376, ":End of /MOTD command")
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
            except LoginError as e:
                log.error(str(e))
                # Todo: send wrong password reply

        def pass_handler():
            if args:
                self.password = args[1:] if args[0] == ":" else args

        def list_handler():
            arguments = args.split(' ')
            available_channels = self.chatapi.get_room_list()
            if len(arguments) == 2:  # ask another server
                pass
            elif len(arguments) == 1:  # expecting comma separated channels, return their topics
                channels = arguments[0].split(',')
                for channel in channels:
                    if channel in available_channels:
                        pass  # TODO
            elif len(arguments) == 0:  # return all rooms
                self.reply(321, "Channel :Users  Name")
                for channel in available_channels:
                    self.reply(322, "#%s %d :%s" % (channel.name.replace(' ', UNICODE_SPACE), channel.users_count, channel.description))
                self.reply(323, ":End of /LIST")
            else:
                self.not_enough_arguments_reply(command)

        def join_handler():
            arguments = args.split(' ')
            rooms = arguments[0].split(',')
            rooms_available = self.chatapi.get_room_list()
            keys = arguments[1].split(',')  # TODO locked rooms
            for room in rooms:
                r = next((x for x in rooms_available if x.name == room), None)
                if r:
                    log.info("Joining room : %s", r.name)
                    self.chatapi.join(r)
                    # TODO RPL_NOTOPIC
                    self.reply(332, "%s :%s" % (r.name, r.description))
                else:
                    log.info("Failed to join : %s", room)
                    # TODO room not found

        def quit_handler():
            self.chatapi.logout()

        def ping_handler():
            pass  # TODO

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

t = ThreadedTCPServer(('localhost', IRC_LISTEN_PORT), IRCServer)  # TODO hostname from config

try:
    log.info("*** ChatCzGate version {0} ***".format(VERSION))
    log.info("Listening on port: {0}".format(IRC_LISTEN_PORT))
    t.serve_forever()
except KeyboardInterrupt:
    sys.exit(0)
