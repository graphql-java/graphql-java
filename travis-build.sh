#!/bin/bash
set -ev
echo "current git hash:"
git rev-parse --short HEAD
BUILD_COMMAND="./gradlew assemble && ./gradlew check --info"
if [ "${TRAVIS_PULL_REQUEST}" = "false" ] && [ "${TRAVIS_BRANCH}" = "master" ]; then
    echo "Building on master"
    BUILD_COMMAND="./gradlew clean assemble && ./gradlew check  --info && ./gradlew bintrayUpload -x check --info"
fi
docker run -it --rm -v `pwd .`:/build -e RELEASE_VERSION=$RELEASE_VERSION -e BINTRAY_USER=$BINTRAY_USER -e BINTRAY_API_KEY=$BINTRAY_API_KEY -e MAVEN_CENTRAL_USER=$MAVEN_CENTRAL_USER -e MAVEN_CENTRAL_PASSWORD=$MAVEN_CENTRAL_PASSWORD -w /build openjdk:8u131-jdk bash -c "${BUILD_COMMAND}"
