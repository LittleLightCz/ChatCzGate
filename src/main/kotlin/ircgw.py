
IRC_HOSTNAME = "localhost"

ENCODING = "UTF-8"
NEWLINE = "\r\n"
VERSION = "1.0"

IRC_PORT = config.getint("IRC Server", "port", fallback=6667)

class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """







    def handle_command(self, command, args):
        """ IRC command handlers """




        # Supported commands
        commands = {  # TODO other commands
            "USER": user_handler,
            "NICK": nick_handler,
            "PASS": pass_handler,
            "LIST": list_handler,
            "JOIN": join_handler,
            "PART": part_handler,
            "WHO": who_handler,
            "WHOIS": whois_handler,
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
            nick = to_ws(user.name)
            channel = to_ws("#" + room.name)

            self.reply_join(nick, channel)
            self.set_user_mode(user, room)

            if user.anonymous:
                self.reply_notice(channel, "INFO: {0} is anonymous".format(nick))

    def user_left(self, room, user):
        if user.name != self.get_nick():
            self.reply_part(to_ws(user.name), to_ws("#"+room.name))

    def system_message(self, room, message):
        self.reply_notice(to_ws("#" + room.name), message)

    def user_mode(self, room, user, mode):
        self.reply_mode(to_ws("#"+room.name), mode, to_ws(user.name))

    def kicked(self, room):
        self.reply_kick(to_ws("#"+room.name), "Reason not implemented yet!")



