cd OnlineJudge
docker build -t rutwik_onlinejudge .
cd ../JudgeServer
docker build -t rutwik_judgeserver .
cd ../OnlineJudgeDeploy
sudo docker-compose up -d