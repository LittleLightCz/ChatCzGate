import os
import re
import sys

import logging

log = logging.getLogger("chat")


class PluginData:
    """
    Data holder class that is passed to each plugin individually to process its content
    """
    def __init__(self, reply=None, command=None):
        self.reply = reply
        self.command = command
        self.result_replies = []
        self.result_commands = []

    def verify_results(self):
        """
        This method makes sure that results will be appropriately set if no plugin takes any action at all.
        """
        if self.reply:
            self.result_replies = self.result_replies or [self.reply]

        if self.command:
            self.result_commands = self.result_commands or [self.command]


class Plugins:

    def __init__(self, path="plug_ins"):
        sys.path.append(path)

        self.plugins = []

        for entry in os.listdir(path):
            if os.path.isfile(os.path.join(path, entry)):
                match = re.search("(.+)\.py(c?)$", entry)
                if match:
                    try:
                        module = __import__(match.group(1))
                        plugin_class = getattr(module, "Plugin")
                        self.plugins.append(plugin_class())
                    except:
                        log.exception("File {0} doesn't contain class Plugin!".format(entry))

    def process(self, data):
        """
        Process data
        :param data: PluginData
            Data containing reply or command to be processed by the plug_ins
        """
        try:
            for plugin in self.plugins:
                if not plugin.process(data):
                    break
        except:
            log.exception("Error occurred in plugin processing")

        # Verify results (in case nothing was processed)
        data.verify_results()
        return data

    def get_loaded_plugins_names(self):
        return [ p.name for p in self.plugins]