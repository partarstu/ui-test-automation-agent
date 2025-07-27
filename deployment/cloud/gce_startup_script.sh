#!/bin/bash
set -e

# This script runs on the GCE VM at startup.

# --- Configuration ---
PROJECT_ID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-project-id" -H "Metadata-Flavor: Google")
SERVICE_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-service-name" -H "Metadata-Flavor: Google")
IMAGE_TAG=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-image-tag" -H "Metadata-Flavor: Google")
NO_VNC_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/no-vnc-port" -H "Metadata-Flavor: Google")
VNC_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/vnc-port" -H "Metadata-Flavor: Google")
AGENT_SERVER_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/agent-server-port" -H "Metadata-Flavor: Google")
AGENT_CONTAINER_LOG_FOLDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/agent-container-log-folder" -H "Metadata-Flavor: Google")
VNC_RESOLUTION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/VNC_RESOLUTION" -H "Metadata-Flavor: Google")
LOG_LEVEL=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/LOG_LEVEL" -H "Metadata-Flavor: Google")
INSTRUCTION_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/INSTRUCTION_MODEL_NAME" -H "Metadata-Flavor: Google")
VISION_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/VISION_MODEL_NAME" -H "Metadata-Flavor: Google")
MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UNATTENDED_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UNATTENDED_MODE" -H "Metadata-Flavor: Google")
DEBUG_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DEBUG_MODE" -H "Metadata-Flavor: Google")
JAVA_APP_STARTUP_SCRIPT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/java-app-startup-script" -H "Metadata-Flavor: Google")

# --- Docker Authentication ---
echo "Configuring Docker to authenticate with Google Container Registry..."
mkdir -p /tmp/.docker
export DOCKER_CONFIG=/tmp/.docker
docker-credential-gcr configure-docker

# --- Install Google Cloud SDK (using containerized gcloud) ---
echo "Pulling google/cloud-sdk image..."
docker pull google/cloud-sdk:latest

# --- Fetch Secrets ---
echo "Fetching secrets from Secret Manager..."
GROQ_API_KEY_SECRET="projects/${PROJECT_ID}/secrets/GROQ_API_KEY/versions/latest"
GROQ_ENDPOINT_SECRET="projects/${PROJECT_ID}/secrets/GROQ_ENDPOINT/versions/latest"
VECTOR_DB_URL_SECRET="projects/${PROJECT_ID}/secrets/VECTOR_DB_URL/versions/latest"
VNC_PW_SECRET="projects/${PROJECT_ID}/secrets/VNC_PW/versions/latest"

GROQ_API_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="GROQ_API_KEY" --project="${PROJECT_ID}")
GROQ_ENDPOINT=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="GROQ_ENDPOINT" --project="${PROJECT_ID}")
VECTOR_DB_URL=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VECTOR_DB_URL" --project="${PROJECT_ID}")
VNC_PW=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VNC_PW" --project="${PROJECT_ID}")

# --- Creating Log Directory on Host ---
echo "Creating log directory on the host..."
mkdir -p /var/log/ui-test-automation-agent

# --- Run Docker Container ---
echo "Removing any existing service containers"
docker rm ${SERVICE_NAME} >/dev/null 2>&1

echo "Pulling and running the Docker container..."
docker run -d --rm --name ${SERVICE_NAME} \
    -p ${NO_VNC_PORT}:${NO_VNC_PORT} \
    -p ${VNC_PORT}:${VNC_PORT} \
    -p ${AGENT_SERVER_PORT}:${AGENT_SERVER_PORT} \
    -v /var/log/ui-test-automation-agent:${AGENT_CONTAINER_LOG_FOLDER} \
    -e GROQ_API_KEY="${GROQ_API_KEY}" \
    -e GROQ_ENDPOINT="${GROQ_ENDPOINT}" \
    -e VECTOR_DB_URL="${VECTOR_DB_URL}" \
    -e VNC_PW="${VNC_PW}" \
    -e VNC_RESOLUTION="${VNC_RESOLUTION}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    -e INSTRUCTION_MODEL_NAME="${INSTRUCTION_MODEL_NAME}" \
    -e VISION_MODEL_NAME="${VISION_MODEL_NAME}" \
    -e logging.level.dev.langchain4j="INFO" \
    -e MODEL_PROVIDER="${MODEL_PROVIDER}" \
    -e UNATTENDED_MODE="${UNATTENDED_MODE}" \
    -e DEBUG_MODE="${DEBUG_MODE}" \
    gcr.io/${PROJECT_ID}/${SERVICE_NAME}:${IMAGE_TAG} ${JAVA_APP_STARTUP_SCRIPT}

echo "Container '${SERVICE_NAME}' is starting."