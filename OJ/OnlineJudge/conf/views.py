import hashlib
import json
import os
import re
import shutil
import smtplib
import time
import logging
from datetime import datetime

import pytz
import requests
from django.conf import settings
from django.utils import timezone
from requests.exceptions import RequestException

from account.decorators import super_admin_required
from account.models import User
from contest.models import Contest
from judge.dispatcher import process_pending_task
from options.options import SysOptions
from problem.models import Problem
from submission.models import Submission
from utils.api import APIView, CSRFExemptAPIView, validate_serializer
from utils.shortcuts import send_email, get_env
from utils.xss_filter import XSSHtml
from .models import JudgeServer
from .serializers import (CreateEditWebsiteConfigSerializer,
                          CreateSMTPConfigSerializer, EditSMTPConfigSerializer,
                          JudgeServerHeartbeatSerializer,
                          JudgeServerSerializer, TestSMTPConfigSerializer, EditJudgeServerSerializer)
import boto3
s3 = boto3.client("s3")
bucketName=os.environ.get("TEST_CASE_BUCKET","ojtest")
logger = logging.getLogger()

class SMTPAPI(APIView):
    @super_admin_required
    def get(self, request):
        smtp = SysOptions.smtp_config
        if not smtp:
            return self.success(None)
        smtp.pop("password")
        return self.success(smtp)

    @super_admin_required
    @validate_serializer(CreateSMTPConfigSerializer)
    def post(self, request):
        SysOptions.smtp_config = request.data
        return self.success()

    @super_admin_required
    @validate_serializer(EditSMTPConfigSerializer)
    def put(self, request):
        smtp = SysOptions.smtp_config
        data = request.data
        for item in ["server", "port", "email", "tls"]:
            smtp[item] = data[item]
        if "password" in data:
            smtp["password"] = data["password"]
        SysOptions.smtp_config = smtp
        return self.success()


class SMTPTestAPI(APIView):
    @super_admin_required
    @validate_serializer(TestSMTPConfigSerializer)
    def post(self, request):
        if not SysOptions.smtp_config:
            return self.error("Please setup SMTP config at first")
        try:
            send_email(smtp_config=SysOptions.smtp_config,
                       from_name=SysOptions.website_name_shortcut,
                       to_name=request.user.username,
                       to_email=request.data["email"],
                       subject="You have successfully configured SMTP",
                       content="You have successfully configured SMTP")
        except smtplib.SMTPResponseException as e:
            # guess error message encoding
            msg = b"Failed to send email"
            try:
                msg = e.smtp_error
                # qq mail
                msg = msg.decode("gbk")
            except Exception:
                msg = msg.decode("utf-8", "ignore")
            return self.error(msg)
        except Exception as e:
            msg = str(e)
            return self.error(msg)
        return self.success()


class WebsiteConfigAPI(APIView):
    def get(self, request):
        ret = {key: getattr(SysOptions, key) for key in
               ["website_base_url", "website_name", "website_name_shortcut",
                "website_footer", "allow_register", "submission_list_show_all"]}
        return self.success(ret)

    @super_admin_required
    @validate_serializer(CreateEditWebsiteConfigSerializer)
    def post(self, request):
        for k, v in request.data.items():
            if k == "website_footer":
                with XSSHtml() as parser:
                    v = parser.clean(v)
            setattr(SysOptions, k, v)
        return self.success()


class JudgeServerAPI(APIView):
    @super_admin_required
    def get(self, request):
        servers = JudgeServer.objects.all().order_by("-last_heartbeat")
        return self.success({"token": SysOptions.judge_server_token,
                             "servers": JudgeServerSerializer(servers, many=True).data})

    @super_admin_required
    def delete(self, request):
        hostname = request.GET.get("hostname")
        if hostname:
            JudgeServer.objects.filter(hostname=hostname).delete()
        return self.success()

    @validate_serializer(EditJudgeServerSerializer)
    @super_admin_required
    def put(self, request):
        is_disabled = request.data.get("is_disabled", False)
        JudgeServer.objects.filter(id=request.data["id"]).update(is_disabled=is_disabled)
        if not is_disabled:
            process_pending_task()
        return self.success()


class JudgeServerHeartbeatAPI(CSRFExemptAPIView):
    @validate_serializer(JudgeServerHeartbeatSerializer)
    def post(self, request):
        data = request.data
        client_token = request.META.get("HTTP_X_JUDGE_SERVER_TOKEN")
        if hashlib.sha256(SysOptions.judge_server_token.encode("utf-8")).hexdigest() != client_token:
            return self.error("Invalid token")

        try:
            server = JudgeServer.objects.get(hostname=data["hostname"])
            server.judger_version = data["judger_version"]
            server.cpu_core = data["cpu_core"]
            server.memory_usage = data["memory"]
            server.cpu_usage = data["cpu"]
            server.service_url = data["service_url"]
            server.ip = request.ip
            server.last_heartbeat = timezone.now()
            server.save(update_fields=["judger_version", "cpu_core", "memory_usage", "service_url", "ip", "last_heartbeat"])
        except JudgeServer.DoesNotExist:
            JudgeServer.objects.create(hostname=data["hostname"],
                                       judger_version=data["judger_version"],
                                       cpu_core=data["cpu_core"],
                                       memory_usage=data["memory"],
                                       cpu_usage=data["cpu"],
                                       ip=request.META["REMOTE_ADDR"],
                                       service_url=data["service_url"],
                                       last_heartbeat=timezone.now(),
                                       )
        # 新server上线 处理队列中的，防止没有新的提交而导致一直waiting
        process_pending_task()

        return self.success()


class LanguagesAPI(APIView):
    def get(self, request):
        return self.success({"languages": SysOptions.languages, "spj_languages": SysOptions.spj_languages})


class TestCasePruneAPI(APIView):
    @super_admin_required
    def get(self, request):
        """
        return orphan test_case list
        """
        ret_data = []
        dir_to_be_removed = self.get_orphan_ids()

        # return an iterator
        for d in dir_to_be_removed:
            ret_data.append({"id": d[0], "create_time": str(d[1])})
        return self.success(ret_data)

    @super_admin_required
    def delete(self, request):
        test_case_id = request.GET.get("id")
        if test_case_id:
            self.delete_one(test_case_id)
            return self.success()
        for id in self.get_orphan_ids():
            self.delete_one(id)
        return self.success()

    @staticmethod
    def get_orphan_ids():
        db_ids = Problem.objects.all().values_list("test_case_id", flat=True)
        db_ids = [x+".zip" for x in db_ids]
        try:
            disk_ids = s3.list_objects(Bucket=bucketName)['Contents']
            disk = [(s["Key"],s["LastModified"].strftime("%Y-%m-%d %H:%M:%S")) for s in disk_ids]
        except Exception as e:
            db_ids = []
            disk = []
            logger.exception(e)
        listOfUsedList = [x for x in disk if x[0] not in db_ids]
        logger.exception(listOfUsedList)
        return listOfUsedList

    @staticmethod
    def delete_one(id):
        try:
            s3.delete_object(Bucket=bucketName,Key=id+".zip")
            return True
        except Exception as e:
            logger.exception(e)
            return False


class ReleaseNotesAPI(APIView):
    def get(self, request):
        try:
            resp = requests.get("https://raw.githubusercontent.com/QingdaoU/OnlineJudge/master/docs/data.json?_=" + str(time.time()),
                                timeout=3)
            releases = resp.json()
        except (RequestException, ValueError):
            return self.success()
        with open("docs/data.json", "r") as f:
            local_version = json.load(f)["update"][0]["version"]
        releases["local_version"] = local_version
        return self.success(releases)


class DashboardInfoAPI(APIView):
    def get(self, request):
        today = datetime.today()
        today_submission_count = Submission.objects.filter(
            create_time__gte=datetime(today.year, today.month, today.day, 0, 0, tzinfo=pytz.UTC)).count()
        recent_contest_count = Contest.objects.exclude(end_time__lt=timezone.now()).count()
        judge_server_count = len(list(filter(lambda x: x.status == "normal", JudgeServer.objects.all())))
        return self.success({
            "user_count": User.objects.count(),
            "recent_contest_count": recent_contest_count,
            "today_submission_count": today_submission_count,
            "judge_server_count": judge_server_count,
            "env": {
                "FORCE_HTTPS": get_env("FORCE_HTTPS", default=False),
                "STATIC_CDN_HOST": get_env("STATIC_CDN_HOST", default="")
            }
        })
