import logging
import socketserver
import sys

from chatapi import ChatAPI, ChatEvent

IRC_LISTEN_PORT = 32132  # TODO get from config file
UNICODE_SPACE = u'\xa0'

log = logging.getLogger("chat")

class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """

    line_separator = '\r\n'  # TODO regex

    def __init__(self, request, client_address, server):
        super().__init__(request, client_address, server)
        self.chatapi = ChatAPI(self)
        self.nickname = ''

    def handle(self):
        """ Handles incoming message """
        socket_file = self.request.makefile("r")
        while True:
            self.parse_line(socket_file.readline())

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        split_line = line.split(' ')  # TODO regex to handle ':' data containing spaces
        command = split_line[0]
        args = split_line[1:]  # TODO improve logic
        log.debug("Parsed command %s args: %s " % (command, args))  # TODO debug only - unsafe, remove
        self.handle_command(command, args)

    def reply(self, response_number, message):
        """ Send response to client """
        # TODO get server name
        response = ":%s %d %s %s%s" % ('localhost', response_number, self.nickname, message, self.line_separator)
        log.debug("Sending: %s" % response)
        self.request.send(str.encode(response))

    def not_enough_arguments_reply(self, command_name):
        self.reply(461, "%s :Not enough parameters" % command_name)

    def handle_command(self, command, args):
        """ IRC command handlers """
        def user_handler():
            if len(args) == 4:
                self.nickname = args[0]  # TODO take nickname from USER or NICK ?
                self.reply(1, "Welcome to ChatCzGate!")
                self.reply(376, ":End of /MOTD command")
            else:
                self.not_enough_arguments_reply(command)

        def nick_handler():
            self.nickname = args[0]

        def pass_handler():
            # password = args[0][1:]
            # TODO if password is set, use non-anonymous login
            pass

        def list_handler():
            available_channels = self.chatapi.get_room_list()
            if len(args) == 2:  # ask another server
                pass
            elif len(args) == 1:  # expecting comma separated channels, return their topics
                channels = args[0].split(',')
                for channel in channels:
                    if channel in available_channels:
                        pass  # TODO
            elif len(args) == 0:  # return all rooms
                self.reply(321, "Channel :Users  Name")
                for channel in available_channels:
                    self.reply(322, "#%s %d :%s" % (channel.name.replace(' ', UNICODE_SPACE), channel.users_count, channel.description))
                self.reply(323, ":End of /LIST")
            else:
                self.not_enough_arguments_reply(command)

        commands = {  # TODO other commands
            # """ Supported commands -> handlers map """
            "USER": user_handler,
            "NICK": nick_handler,
            "PASS": pass_handler,
            "LIST": list_handler
        }

        try:
            log.debug("requesting command : %s", command)
            commands[command]()
        except KeyError:
            log.error("IRC command not found: %s", command)

    def new_message(self, room, user, text, whisper):
        super().new_message(room, user, text, whisper) # Todo

    def user_joined(self, room, user):
        super().user_joined(room, user) # Todo

    def user_left(self, room, user):
        super().user_left(room, user) # Todo


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    """ Allows multiple clients """
    pass

t = ThreadedTCPServer(('localhost', IRC_LISTEN_PORT), IRCServer)  # TODO hostname from config

try:
    t.serve_forever()
except KeyboardInterrupt:
    sys.exit(0)
