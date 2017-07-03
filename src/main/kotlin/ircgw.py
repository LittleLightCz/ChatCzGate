
IRC_HOSTNAME = "localhost"

ENCODING = "UTF-8"
NEWLINE = "\r\n"
VERSION = "1.0"

IRC_PORT = config.getint("IRC Server", "port", fallback=6667)

class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """



    def send_who_user_info(self, room, user):
        nick = to_ws(user.name)
        host = self.hostname
        gender = user.gender.value
        op = ""  # Admin SS DS ?
        response = "#%s %s@%s unknown %s %s %s %s:0 %s %s" % \
                   (to_ws(room.name), nick, host, host, nick, gender, op, nick, NEWLINE)
        self.socket_send(response)

    def set_user_mode(self, user, room):
        """Sets right MODE for specific user"""
        nick = to_ws(user.name)
        channel = to_ws("#" + room.name)

        # Mark girl
        if user.gender == Gender.FEMALE:
            self.reply_mode(channel, "+v", nick)

        # Mark operator
        if user.name in room.admin_list:
            self.reply_mode(channel, "+o", nick)

        # Mark half-operator
        if user.id == room.operator_id:
            self.reply_mode(channel, "+h", nick)

        # Mark room admin
        if user.admin:
            self.reply_mode(channel, "+A", nick)

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
                    self.socket_send(":%s!%s@%s JOIN #%s%s" % (self.get_nick(), self.username, self.hostname, room_name, NEWLINE))
                    self.reply(332, "#%s :%s" % (room_name, r.description))
                    users_in_room = ' '.join([to_ws(x.name) for x in r.user_list])
                    self.reply(353, "= #%s :%s %s" % (room_name, self.get_nick(), users_in_room))
                    self.reply(366, "#%s :End of /NAMES list." % room_name)
                else:
                    log.error("Couldn't find the room for name: {0}".format(room))

        def who_handler():
            arguments = args.split(' ')
            room_name = re.sub(r"^#", "", from_ws(arguments[0]))
            room = self.chatapi.get_active_room_by_name(room_name)
            for user in room.user_list:
                self.send_who_user_info(room, user)

            self.reply(315, ":End of WHO list")

            # Set user modes
            for user in room.user_list:
                self.set_user_mode(user, room)

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

        def ping_handler():
            pong = ":%s PONG %s :%s %s" % (self.hostname, self.hostname, args, NEWLINE)
            self.socket_send(pong)

        def whois_handler():
            m = next(re.finditer(r"[^ ]+", args), None)
            if m:
                nick = from_ws(m.group())
                profile = self.chatapi.get_user_profile(nick)
                if profile:
                    self.reply_notice_all("=== WHOIS Profile ===")
                    self.reply_notice_all("Anonym: {0}".format(profile.anonymous))
                    self.reply_notice_all("Nick: {0}".format(profile.nick))

                    if profile.age:
                        self.reply_notice_all("Věk: {0}".format(profile.age))

                    self.reply_notice_all("Pohlaví: {0}".format(profile.gender.name.title()))
                    # self.reply_notice_all("Karma: {0}".format(profile.karma))
                    # self.reply_notice_all("Registrace: {0}".format(profile.registration))
                    # self.reply_notice_all("Naposledy: {0}".format(profile.last_seen))

                    if profile.viewed:
                        self.reply_notice_all("Profil zobrazen: {0}x".format(profile.viewed))

                    # Temporarily removed because REST returns just small picture thumbnail URL
                    # if profile.imageUrl:
                    #     self.reply_notice_all("Profilovka: {0}".format(profile.imageUrl))

                    self.reply_notice_all("Online: {0}".format(profile.online))

                    if profile.rooms:
                        channels = [to_ws("#"+room) for room in profile.rooms]
                        self.reply_notice_all("Chatuje v: {0}".format(", ".join(channels)))

                    if not profile.anonymous:
                       self.reply_notice_all("Profil: {0}".format(profile.url))


                    self.reply_notice_all("=== End Of WHOIS Profile ===")
                else:
                    self.reply_notice_all("WHOIS: Failed to get profile of: %s" % to_ws(nick))

        def oper_handler():
            pass  # TODO

        def mode_handler():
            m = next(re.finditer(r"#([^ ]+) \+(\w+) (.+)", args), None)
            if m:
                room_name, modes, nick = m.groups()
                room = self.chatapi.get_active_room_by_name(from_ws(room_name))

                if room:
                    if "o" in modes:
                        last_admin_id = room.operator_id
                        self.chatapi.admin(room, from_ws(nick))
                        # Now remove the half-operator from user, that was former half-operator
                        user = UserDb.get_user_by_uid(last_admin_id)
                        if user:
                            self.reply_mode("#"+room_name, "-h", to_ws(user.name))
                else:
                    log.error("Failed to get room by name: "+room_name)

        def topic_handler():
            pass  # TODO

        def names_handler():
            pass  # TODO

        def invite_handler():
            pass  # TODO

        def kick_handler():
            match = re.match(r"#(\S+)\s+(\S+)\s+:(.+)", args)

            try:
                if match:
                    room_name = from_ws(match.group(1))
                    user = from_ws(match.group(2))
                    reason = match.group(3)

                    room = self.chatapi.get_active_room_by_name(room_name)
                    if room:
                        self.chatapi.kick(room, user, reason)
                    else:
                        log.error("Failed to get room by name: "+room_name)
            except:
                msg = "Failed to kick user {0} from the room {1}!".format(user, room_name)
                log.exception(msg)
                self.reply_notice_all(msg)

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



