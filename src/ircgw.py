import logging as log
import socketserver

# TODO get from config file
import sys

IRC_LISTEN_PORT = 32132



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


class IRCServer(socketserver.BaseRequestHandler):
    """ IRC connection handler """

    line_separator = '\r\n'  # TODO regex
    recv_buffer = ''

    def handle(self):
        """ Handles incoming message """
        data = self.request.recv(1024).strip()
        log.debug("%s says %s" % (self.client_address[0], data))
        self.recv_buffer += data
        self.parse_recv_buffer()

    def parse_recv_buffer(self):
        """ Parse incoming message to lines """
        lines = self.line_separator.split(self.recv_buffer)
        self.recv_buffer = lines[-1]  # leave last (maybe incomplete) line in buffer

        for line in lines[:-1]:
            log.debug("Parsing line: %s" % line)
            self.parse_line(line)

    def parse_line(self, line):
        """ Parse lines and call command handlers """
        split_line = line.split(' ')
        command = split_line[0]
        args = split_line[1:]  # TODO improve logic
        self.handle_command(command, args)
        log.debug("Parsed command %s" % command)

    def handle_command(self, command, args):
        if command in self.commands:
            pass  # TODO call handlers from commands map

    def user_handler(self, args):
        pass  # TODO

    def nick_handler(self, args):
        pass  # TODO

    commands = {  # TODO other commands
        """ Supported commands -> handlers map """
        "USER": user_handler,
        "NICK": nick_handler,
    }


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    """ Allows multiple clients """
    pass

t = ThreadedTCPServer(('localhost', IRC_LISTEN_PORT), IRCServer)

try:
    t.serve_forever()
except KeyboardInterrupt:
    sys.exit(0)
