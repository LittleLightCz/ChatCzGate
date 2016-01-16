import time
import logging
import os
import zipfile

from conf import config

LOGGER_FORMAT = '%(asctime)s %(name)-12s %(levelname)-8s %(message)s'

"""
Store too big logs into logs backup
"""
log_file = "log.txt"
logs_backup_dir = "logs_backup"
size_limit = 10000000

if not os.path.exists(logs_backup_dir):
    os.mkdir(logs_backup_dir)

size = os.path.getsize(log_file)
if size > size_limit:
    print("Log file size is: {0} bytes. Moving it to the backup folder ...".format(size))
    zip_name = time.strftime("%Y-%m-%d_%Hh%Mm%Ss-log.zip")
    z = zipfile.ZipFile(os.path.join(logs_backup_dir, zip_name), "w", zipfile.ZIP_LZMA)
    z.write(log_file)
    z.close()

    #remove the log file
    os.remove(log_file)


"""
Main logger init
"""
# Init logger
log = logging.getLogger('chat')
log.setLevel(config.get("Global", "loglevel", fallback="INFO")) # or whatever
handler = logging.FileHandler(log_file, 'a', 'utf-8') # or whatever
handler.setFormatter = logging.Formatter('%(name)s %(message)s') # or whatever
log.addHandler(handler)

# define a Handler which writes INFO messages or higher to the sys.stderr
console = logging.StreamHandler()
console.setLevel(logging.DEBUG)
# set a format which is simpler for console use
formatter = logging.Formatter(LOGGER_FORMAT)
# tell the handler to use this format
console.setFormatter(formatter)
# add the handler to the root logger
log.addHandler(console)