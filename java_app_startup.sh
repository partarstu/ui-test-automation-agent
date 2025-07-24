#!/bin/bash

# This script is executed after the main VNC and desktop services are running.

# --- Wait for X server to be ready (with timeout) ---
# This loop waits until the X11 socket for display :1 exists.
MAX_RETRIES=60 # 60 retries * 0.5s sleep = 30 seconds timeout
RETRY_COUNT=0

echo "Waiting for X server on display :1 to be ready..."

while [ ! -e /tmp/.X11-unix/X1 ]; do
  if [ ${RETRY_COUNT} -ge ${MAX_RETRIES} ]; then
    echo "ERROR: Timed out after ${MAX_RETRIES} retries. X server did not start." >&2
    exit 1
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  sleep 0.5
done

echo "X server is ready."

echo "Launching Java application as 'headless' user and redirecting output to /home/headless/java_app.log"

# --- Launch the Java Application with Logging ---
# We use 'su -c' to run the command as the 'headless' user.
# This ensures the Java app has the correct permissions to connect to the
# desktop session and to create the log file in its own home directory.
cd /tmp
su -c "java -jar /app/ui_test_java-1.0.0-SNAPSHOT.jar"

echo "Java application launch command issued."