import logging
import re
import socketserver
import sys
from threading import Lock

from chatapi import ChatAPI, ChatEvent
from error import LoginError, MessageError

LINE_BREAK = "\r\n"
ENCODING = "UTF-8"

IRC_HOSTNAME = 'localhost'
IRC_LISTEN_PORT = 32132  # TODO get from config file
UNICODE_SPACE = u'\xa0'

VERSION = "0.1"

log = logging.getLogger("chat")


def to_ws(text):
    """Convenience function for converting spaces into unicode whitespaces"""
    return text.replace(' ', UNICODE_SPACE)


def from_ws(text):
    """Convenience function for converting unicode whitespaces into spaces"""
    return text.replace(UNICODE_SPACE, ' ')


class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """

    def __init__(self, request, client_address, server):
        self.chatapi = ChatAPI(self)
        self.nickname = None
        self.username = None
        self.password = None
        self.hostname = IRC_HOSTNAME
        self._socket_lock = Lock()
        super().__init__(request, client_address, server)

    def handle(self):
        """ Handles incoming message """
        try:
            while True:  # TODO exit
                data = self.request.recv(512).decode('utf-8')  # TODO readline ?
                log.debug("RAW: %s" % data)
                lines = data.split(LINE_BREAK)
                for line in lines:
                    self.parse_line(line)
        except Exception:
            log.exception("Exception during socket reading!")
            try:
                if self.chatapi.logged:
                    log.info("We are still logged, doing logout ...")
                    self.chatapi.logout()
            except Exception:
                log.exception("Failed to logout!")

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        match = re.match(r"(^\w+)\s*(.+)?", line)
        if match:
            command, args = match.groups()
            args = args or ""

            debug_args = re.sub(".", "*", args) if command == "PASS" else args
            log.debug("Parsed command %s args: %s " % (command, debug_args))
            self.handle_command(command, args)

    def get_nick(self):
        return self.nickname or self.username

    def socket_send(self, response):
        """
        The only method that should be used to send data through the socket

        :param response: string
            Message to be sent to the client
        """
        log.debug("Sending: %s" % re.sub(LINE_BREAK+"$","",response))
        with self._socket_lock:
            self.request.send(str.encode(response, encoding=ENCODING))

    def reply_join(self, name, channel):
        """ Send JOIN response to client """
        response = ":%s JOIN %s %s" % (name, channel, LINE_BREAK)
        self.socket_send(response)

    def reply_part(self, name, channel):
        """ Send PART response to client """
        response = ":%s PART %s %s" % (name, channel, LINE_BREAK)
        self.socket_send(response)

    def reply_privmsg(self, sender, to, text):
        """ Send PROVMSG response to client """
        response = ":%s PRIVMSG %s :%s %s" % (sender, to, text, LINE_BREAK)
        self.socket_send(response)

    def reply(self, response_number, message):
        """ Send response to client """
        # TODO get server name
        response = ":%s %03d %s %s %s" % (self.hostname, response_number, self.nickname, message, LINE_BREAK)
        self.socket_send(response)

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
        self.send_MOTD_text("With great power comes great responsibility ...")
        self.reply(376, self.get_nick() + " :End of MOTD command.")

    def send_who_user_info(self, room, user):
        nick = to_ws(user.name)
        host = self.hostname
        gender = user.gender.value
        op = "" # Admin SS DS ?
        response = "#%s %s@%s unknown %s %s %s %s:0 %s %s" % \
                   (to_ws(room.name), nick, host, host, nick, gender, op, nick, LINE_BREAK)
        self.socket_send(response)

    def handle_command(self, command, args):
        """ IRC command handlers """
        def user_handler():
            arguments = args.split(' ')
            if len(arguments) == 4:
                self.username = arguments[0]
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
                self.reply(322, "#%s %d :%s" % (to_ws(channel.name), channel.users_count, channel.description))
            self.reply(323, ":End of /LIST")

        def part_handler():
            arguments = args.split(' ')
            rooms = arguments[0].split(',')
            for room in rooms:
                # Remove leading hash sign
                room = re.sub(r"^#", "", from_ws(room))
                r = self.chatapi.get_active_room_by_name(room)
                if r:
                    log.info("Leaving room : %s", r.name)
                    self.chatapi.part(r)
                    self.reply_part(self.get_nick(), "#"+to_ws(room))
                else:
                    log.error("Couldn't find the room for name: ", room)
                    # TODO room not found

        def join_handler():
            arguments = args.split(' ')
            log.debug(" join debug ")
            rooms = arguments[0].split(',')
            keys = arguments[1].split(',') if len(arguments) > 1 else []
            for room in rooms:
                # Remove leading hash sign
                room = re.sub(r"^#", "", from_ws(room))
                r = self.chatapi.get_room_by_name(room)
                if r:
                    log.info("Joining room : %s", r.name)
                    self.chatapi.join(r)
                    # TODO RPL_NOTOPIC
                    room_name = to_ws(r.name)
                    self.socket_send(":%s!%s@%s JOIN #%s%s" % (self.nickname, self.username, self.hostname, room_name, LINE_BREAK))
                    self.reply(332, "#%s :%s" % (room_name, r.description))
                    users_in_room = ' '.join([to_ws(x.name) for x in r.user_list])
                    self.reply(353, "= #%s :%s %s" % (room_name, self.nickname, users_in_room))
                    self.reply(366, "#%s :End of /NAMES list." % room_name)
                else:
                    log.error("Couldn't find the room for name: ", room)
                    # TODO room not found

        def who_handler():
            arguments = args.split(' ')
            room_name = re.sub(r"^#", "", from_ws(arguments[0]))
            room = self.chatapi.get_active_room_by_name(room_name)
            for user in room.user_list:
                self.send_who_user_info(room, user)

            self.reply(315, ":End of WHO list")

        def privmsg_handler():
            match = re.match(r"(.+?)\s*:(.*)", args)
            target, msg = match.groups()
            target = from_ws(target)
            try:
                if target[0] == '#':  # send to channel
                    room = self.chatapi.get_active_room_by_name(target[1:])
                    self.chatapi.say(room, msg)
                else:  # whisper
                    self.chatapi.whisper(target, msg)
            except MessageError as e:
                self.reply_privmsg('ChatCzGate', self.get_nick(), e)

        def quit_handler():
            self.chatapi.logout()

        def ping_handler():
            log.debug(args)
            pong = "PONG %s :1 %s%s" % (args, self.hostname, LINE_BREAK)  # TODO check
            self.socket_send(pong)

        def oper_handler():
            pass  # TODO

        def mode_handler():
            pass  # TODO

        def topic_handler():
            pass  # TODO

        def names_handler():
            pass  # TODO

        def invite_handler():
            pass  # TODO

        def kick_handler():
            pass  # TODO

        # Supported commands
        commands = {  # TODO other commands
            "USER": user_handler,
            "NICK": nick_handler,
            "PASS": pass_handler,
            "LIST": list_handler,
            "JOIN": join_handler,
            "PART": part_handler,
            "WHO": who_handler,
            "QUIT": quit_handler,
            "PING": ping_handler,
            "PRIVMSG": privmsg_handler,
            "OPER": oper_handler,
            "MODE": mode_handler,
            "TOPIC": topic_handler,
            "NAMES": names_handler,
            "INVITE": invite_handler,
            "KICK": kick_handler,
        }

        try:
            log.debug("requesting command : %s", command)
            # Execute command handler
            commands[command]()
        except KeyError:
            log.error("IRC command not found: %s", command)
        except Exception:
            log.exception("Error during command handling!")

    def new_message(self, room, user, text, whisper):
        if user.name != self.get_nick():
            to = self.get_nick() if whisper else "#"+room.name
            self.reply_privmsg(to_ws(user.name), to_ws(to), text)

    def user_joined(self, room, user):
        if user.name != self.get_nick():
            self.reply_join(to_ws(user.name), to_ws("#"+room.name))

    def user_left(self, room, user):
        if user.name != self.get_nick():
            self.reply_part(to_ws(user.name), to_ws("#"+room.name))


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
