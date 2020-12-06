#!/bin/bash
rm -rf /judger/*
mkdir -p /judger/run /judger/spj

chown compiler:code /judger/run
chmod 711 /judger/run

chown compiler:spj /judger/spj
chmod 710 /judger/spj

core=$(grep --count ^processor /proc/cpuinfo)
n=$(($core * 2 + 1))
mkdir ../log
mkdir ../test_case
chmod 777 ../test_case
chmod 777 ../log
exec gunicorn --workers $n --threads $n --log-level info --access-logfile "-" --error-logfile "-" --time 600 --bind 0.0.0.0:8080 server:app
