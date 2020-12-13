import boto3
import os

s3_client = boto3.client('s3')
s3_resource = boto3.resource('s3')