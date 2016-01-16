import re
import requests as req

from logger import log
from tools import to_ws


class Plugin:
    """
    ==================
    = Jdem plugin: =
    ==================
    This plugins transforms all URLs into shortened version using jdem.cz
    """

    def __init__(self):
        self.jdem_url = "http://www.jdem.cz/get"
        self.name = "Jdem.cz"
        self.nick = None
        self.enabled = True

    def get_notice_message(self, channel, text):
        sender = self.name

        # if it is user, it is whispering message, we need to switch the sender and "channel"
        if channel[0] != '#':
            sender = channel
            channel = self.nick

        return ":%s NOTICE %s :%s %s" % (sender, to_ws(channel), text, "\n")

    def shorten(self, url, irc_command, data):
        """
        Shortens URL
        :param url: string
        :param irc_command: string
        :param data: string
        :return: string
        """

        # Parse channel
        channel = "ERROR"
        m = re.match(r"PRIVMSG\s+([^ ]+)\s*:", irc_command, flags=re.DOTALL)
        if m:
            channel = m.group(1)
            try:
                resp = req.get(self.jdem_url, params={"url": url})
                if resp.status_code == 200:
                    data.result_replies.append(self.get_notice_message(channel, "URL shortened to: {0}".format(resp.text)))
                    return resp.text
            except:
                pass
        else:
            log.error("Failed to parse the channel.")

        failure_msg = "The URL failed to shorten!"
        data.result_replies.append(self.get_notice_message(channel, failure_msg))
        log.warning(failure_msg)
        return url

    def process(self, data):
        """
        Process data
        :param data: PluginData
        :return True if data should be processed by another plugin in the list, or False otherwise
        """

        # Process data ....
        if data.command:
            # replace URLs
            m = re.match(r"(PRIVMSG.+?:)(.+)", data.command, flags=re.DOTALL)
            if m:
                prefix, msg = m.groups()

                if not self.nick:
                    log.error("Your nickname is unknown! Something went wrong ... notice messages will be corrupted.")

                msg = re.sub(r"http.?:\/\/\S+", lambda g: self.shorten(g.group(), prefix, data), msg)
                data.result_commands.append(prefix+msg)

            # save Nick
            m = re.match(r"NICK\s+(\S+)", data.command, flags=re.DOTALL)
            if m:
                self.nick = m.group(1)

        return True
