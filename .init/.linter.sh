#!/bin/bash
cd /home/kavia/workspace/code-generation/android-tv-video-streamer-303773-303782/frontend_android_tv_app
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

