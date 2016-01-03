import os
import re
import sys

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

    for entry in os.listdir(path):
        if os.path.isfile(os.path.join(path, entry)):
            match = re.search("(.+)\.py(c?)$", entry)
            if match:
                globals()[match.groups()[0]] = __import__(match.groups()[0])


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
    except Exception:
        log.exception("Error occurred in plugin processing")

    # Verify results (in case nothing was processed)
    data.verify_results()


# example

import_plugins()

smileys_plugin = smileys.SmileysPlugin()
# TODO maybe move construct to plugins loader ?
# TODO how to correct syntax highlight ?
