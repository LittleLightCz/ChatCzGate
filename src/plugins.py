import os
import re
import sys

import logging


log = logging.getLogger("chat")

class PluginData:
    """
    Data holder class that is passed to each plugin individually to process its content
    """
    def __init__(self, reply=None, command=None, result_replies=[], result_commands=[]):
        self.reply = reply
        self.command = command
        self.result_replies = result_replies
        self.result_commands = result_commands

    def verify_results(self):
        """
        This method makes sure that results will be appropriately set if no plugin takes any action at all.
        """
        if self.reply:
            self.result_replies = self.result_replies or [self.reply]

        if self.command:
            self.result_commands = self.result_commands or [self.command]

def import_plugins(path='plugins'):
    sys.path.append(path)

    global plugins
    plugins = []

    for entry in os.listdir(path):
        if os.path.isfile(os.path.join(path, entry)):
            match = re.search("(.+)\.py(c?)$", entry)
            if match:
                plugins.append(__import__(match.group(1)))


def process(data):
    """
    Process data
    :param data: PluginData
        Data containing reply or command to be processed by the plugins
    """
    try:
        for plugin in plugins:
            if not plugin.process(data):
                break
    except:
        log.exception("Error occurred in plugin processing")

    # Verify results (in case nothing was processed)
    data.verify_results()
    return data


