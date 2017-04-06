#!/bin/bash
set -ev
BUILD_COMMAND="./gradlew assemble && ./gradlew check"
if [ "${TRAVIS_PULL_REQUEST}" = "false" ] && [ "${TRAVIS_BRANCH}" = "master" ]; then
    echo "Building on master"
    BUILD_COMMAND="./gradlew assemble && ./gradlew check && ./gradlew bintrayUpload -x check --info"
fi
#docker create -v /usr/lib/jvm/java-6-openjdk-amd64/jre/lib --name java6-rt java:6 /bin/true
#docker run -it --rm --volumes-from java6-rt -v `pwd .`:/build -e JDK6_HOME=/usr/lib/jvm/java-6-openjdk-amd64 -e BINTRAY_USER=$BINTRAY_USER -e BINTRAY_API_KEY=$BINTRAY_API_KEY -w /build openjdk:8u111-jdk bash -c "${BUILD_COMMAND}"
docker run -it --rm -v `pwd .`:/build  -e BINTRAY_USER=$BINTRAY_USER -e BINTRAY_API_KEY=$BINTRAY_API_KEY -w /build openjdk:8u111-jdk bash -c "${BUILD_COMMAND}"
