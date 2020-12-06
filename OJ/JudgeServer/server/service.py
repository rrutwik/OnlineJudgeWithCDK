import json
import os
import shutil
import requests
import time
from exception import JudgeServiceError
from utils import server_info, logger, token
import sys
from config import (print_to_stdout, JUDGER_WORKSPACE_BASE, SPJ_SRC_DIR, SPJ_EXE_DIR, COMPILER_USER_UID, SPJ_USER_UID,
                    RUN_USER_UID, RUN_GROUP_GID, TEST_CASE_DIR)

class JudgeService(object):
    def __init__(self):
        self.service_url = "http://"+str(os.environ["SERVICE_HOST"])+":"+str(os.environ["SERVICE_PORT"])
        self.backend_url = "http://"+str(os.environ["BACKEND_HOST"])+":"+str(os.environ["BACKEND_PORT"])+"/api/judge_server_heartbeat/"

    def _request(self, data):
        try:
            resp = requests.post(self.backend_url, json=data,
                                 headers={"X-JUDGE-SERVER-TOKEN": token,
                                          "Content-Type": "application/json"}, timeout=5).text
        except Exception as e:
            raise JudgeServiceError("Heartbeat request failed")
        try:
            r = json.loads(resp)
            if r["error"]:
                raise JudgeServiceError(r)
        except Exception as e:
            logger.exception("Heartbeat failed, response is {}".format(resp))
            raise JudgeServiceError("Invalid heartbeat response")

    def heartbeat(self):
        data = server_info()
        data["action"] = "heartbeat"
        data["service_url"] = self.service_url
        self._request(data)

def removeTestCase():
    total, used, free = shutil.disk_usage("/")
    print("Total: %d GiB" % (total // (2**30)), end=" ")
    print("Used: %d GiB" % (used // (2**30)), end=" ")
    print("Free: %d GiB" % (free // (2**30)), end=" ")
    current_time = time.time()
    print(TEST_CASE_DIR, end=" ")
    temp = [os.path.join(TEST_CASE_DIR, name) for name in os.listdir(TEST_CASE_DIR) if os.path.isdir(os.path.join(TEST_CASE_DIR, name)) ]
    if free < 100*1024*1024:
        for f in temp:
            creation_time = os.path.getctime(f)
            if (current_time - creation_time> 20):
                shutil.rmtree(f)
                print('{} removed'.format(f))
    else:
        print("Not Removing Any Test Case")

def healthcare():
    try:
        if not os.environ.get("DISABLE_HEARTBEAT"):
            service = JudgeService()
            print_to_stdout("heartbeating => Service "+ service.service_url + " BACKEND => " + service.backend_url)
            service.heartbeat()
            removeTestCase()
    except Exception as e:
        logger.exception(e)
        raise e

if __name__ == "__main__":
    try:
        if not os.environ.get("DISABLE_HEARTBEAT"):
            service = JudgeService()
            print_to_stdout("heartbeating => Service "+ service.service_url + " BACKEND => " + service.backend_url)
            service.heartbeat()
        exit(0)
    except Exception as e:
        logger.exception(e)
        exit(1)
