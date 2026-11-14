# The original base image (openjdk:8-jdk-alpine) was removed from Docker Hub.
# eclipse-temurin is a recommended replacement; 21-jdk-alpine keeps Alpine
# (so the apk line below still works) and runs on both amd64 and arm64.
FROM eclipse-temurin:21-jdk-alpine

LABEL maintainer="Wagner Franchin"

ENV WEBPATH=/webserver/
ENV WEBSERVER="WebServerSocket"
# 0 keeps the original cached thread pool; N > 0 uses a fixed pool of N threads.
ENV POOL_SIZE=0

RUN apk update && apk add ca-certificates wget && update-ca-certificates

RUN mkdir -p $WEBPATH
COPY src/ $WEBPATH
WORKDIR $WEBPATH

RUN javac *.java task/*.java

EXPOSE 8000

CMD java $WEBSERVER
