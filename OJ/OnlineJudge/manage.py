#!/usr/bin/env python
import os
import sys
import logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)
logging.getLogger('boto').setLevel(logging.ERROR)
logFormatter = logging.Formatter\
("%(name)-12s %(asctime)s %(levelname)-8s %(filename)s:%(funcName)s %(message)s")
consoleHandler = logging.StreamHandler()
consoleHandler.setFormatter(logFormatter)
logger.addHandler(consoleHandler)

if __name__ == "__main__":
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "oj.settings")

    from django.core.management import execute_from_command_line
    import django
    sys.stdout.write("Django VERSION " + str(django.VERSION) + "\n")

    execute_from_command_line(sys.argv)
