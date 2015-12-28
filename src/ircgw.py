import logging as log
import socketserver
import sys

from chatapi import ChatAPI

IRC_LISTEN_PORT = 32132  # TODO get from config file


class IRCServer(socketserver.StreamRequestHandler):
    """ IRC connection handler """

    line_separator = '\r\n'  # TODO regex
    recv_buffer = ''
    nickname = ''

    chatapi = ChatAPI()

    def handle(self):
        """ Handles incoming message """
        while True:
            data = self.request.recv(512).decode('utf-8')
            lines = data.split(self.line_separator)
            self.recv_buffer = lines[-1]  # leave last (maybe incomplete) line in buffer
            for line in lines[:-1]:
                self.parse_line(line)

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        split_line = line.split(' ')
        command = split_line[0]
        args = split_line[1:]  # TODO improve logic
        log.debug("Parsed command %s args: %s " % (command, args))
        self.handle_command(command, args)

    def reply(self, response_number, message):
        """ Send response to client """
        # TODO get server name
        response = ":%s %d %s %s%s" % ('localhost', response_number, self.nickname, message, self.line_separator)
        log.debug("Sending: %s" % response)
        self.request.send(str.encode(response))

    def handle_command(self, command, args):
        """ IRC command handlers """
        def user_handler():
            self.nickname = args[0]
            # hostname = args[1]
            # servername = args[2]
            # real_name = args[3][1:]
            self.reply(1, "Welcome to ChatCzGate!")
            self.reply(376, ":End of /MOTD command")

        def nick_handler():
            self.nickname = args[0]
            self.reply(2, "Test")

        def pass_handler():
            # password = args[0][1:]
            # TODO if password is set, use non-anonymous login
            pass

        def list_handler():
            available_channels = self.chatapi.get_room_list()

            if len(args) == 2:  # ask another server
                pass

            if len(args) == 1:  # expecting comma separated channels, return their topics
                channels = args[0].split(',')
                for channel in channels:
                    if channel in available_channels:
                        pass  # TODO
            else:  # return all rooms
                self.reply(321, "Channel :Users  Name")
                for channel in available_channels:  # TODO implement user count
                    self.reply(322, "#%s %d :%s" % (channel.name.replace(' ', u'\xa0'), 0, channel.description))
                self.reply(323, ":End of /LIST")

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


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    """ Allows multiple clients """
    pass

t = ThreadedTCPServer(('localhost', IRC_LISTEN_PORT), IRCServer)

try:
    t.serve_forever()
except KeyboardInterrupt:
    sys.exit(0)
