
IRC_HOSTNAME = "localhost"

ENCODING = "UTF-8"
NEWLINE = "\r\n"
VERSION = "1.0"

IRC_PORT = config.getint("IRC Server", "port", fallback=6667)

class IRCServer(socketserver.StreamRequestHandler, ChatEvent):
    """ IRC connection handler """





