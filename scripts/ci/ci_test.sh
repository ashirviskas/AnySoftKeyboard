#!/usr/bin/env bash

./gradlew --stacktrace testDebugUnitTest testDebugUnitTestCoverage -PexcludeTestClasses="**/*AllSdkTest*"
