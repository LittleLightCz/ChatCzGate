import os
import re
import sys


def import_plugins(path='plugins'):
    sys.path.append(path)

    for entry in os.listdir(path):
        if os.path.isfile(os.path.join(path, entry)):
            match = re.search("(.+)\.py(c?)$", entry)
            if match:
                globals()[match.groups()[0]] = __import__(match.groups()[0])


# example

import_plugins()

smileys_plugin = smileys.SmileysPlugin()
# TODO maybe move construct to plugins loader ?
# TODO how to correct syntax highlight ?
