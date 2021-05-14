# analysis-server
A Java sensor data analysis server written for the class `Object-Oriented Programming 2` at TU Graz (Wintersemester 2020/21).

This code was produced as group work with Stefan Höllbacher, Adna Ribo and Clemens Hofmann.

My contributions were mostly on the client interace, the cache and some general anaylsis server functionality. 
Design decisions are documented in [`SUBMISSION.md`](https://github.com/sarahalang/analysis-server/blob/main/SUBMISSION.md).


In order to run this, both the client and server need to be started up in different terminals (the server needs to be running when the client is started up). 
To build:
```
# sudo ./docker/createDocker.sh
sudo ./docker/mvn.sh
sudo ./docker/server.sh
```

---

Either start the client up with `sudo ./docker/client.sh` or pass commands as an argument.
```
sudo ./docker/client.sh ls

sudo ./docker/client.sh data 4795 humidity 2020-01-05'T'12:00:01 2020-01-05'T'12:10:17 MAX 1m
data 4795 humidity 2020-01-05'T'12:00:01 2020-01-05'T'12:10:00 MEAN 1m
data 4795 humidity 2020-01-05'T'12:00:01 2020-01-05'T'12:10:17 MEDIAN 1m
data 7820 temperature 2020-09-01'T'00:06:18 2020-09-01'T'00:36:30
data 7820 temperature 2020-09-01'T'00:06:18 2020-09-01'T'00:36:30 MIN


cluster 1503,1693 P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100
cluster all P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100
/docker/client.sh cluster 1779,2043,7819,3735,3841,3891,3993,4021,4430,4963,5734,5819,6493,7263,7440,7645 P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100

./docker/client.sh data 4795 temperature 2018-08-24'T'00:00:00 2019-08-30'T'00:00:00 min 30d


data 4795 temperature 2018-08-24T00:00:00 2019-08-30T00:00:00 min 30d

data 4795 temperature 2018-08-24T00:00:00 2019-08-30T00:00:00 max 30d


./docker/client.sh cluster 1503 P1 2018-01-01 2018-01-02 1h mean 15 2 2 1.0 0.5 10000 0xCafe 100
```
