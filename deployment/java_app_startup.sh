#!/bin/bash
set -e

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

if [ "$DEPLOYMENT_ENV" = "cloud" ]; then
  echo "Cloud deployment detected. Starting websockify with SSL in order to serve noVNC on HTTPS"
  /app/start_websockify_ssl.sh
else
  echo "Local deployment detected. Skipping websockify SSL startup."
fi

echo "Launching Java application from ${APP_JAR_PATH}"
# Check if the APP_JAR_PATH is set and the file exists
if [ -z "${APP_JAR_PATH}" ] || [ ! -f "${APP_JAR_PATH}" ]; then
  echo "ERROR: APP_JAR_PATH environment variable is not set or the file does not exist at '${APP_JAR_PATH}'." >&2
  exit 1
fi

su -c "java -jar ${APP_JAR_PATH}" &

echo "Java application launch command issued."