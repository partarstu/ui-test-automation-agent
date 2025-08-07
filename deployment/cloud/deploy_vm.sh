#!/bin/bash
set -e

# This script provisions a GCE VM and deploys the UI test automation agent.

# --- Configuration ---
# Get the GCP Project ID from the active gcloud configuration.
export PROJECT_ID=$(gcloud config get-value project)

if [ -z "$PROJECT_ID" ]; then
  echo "Error: No active GCP project is configured."
  echo "Please use 'gcloud config set project <project-id>' to set a project."
  exit 1
fi

echo "Using GCP Project ID: $PROJECT_ID"

# You can change these values if needed.
export REGION="${REGION:-us-central1}"
export ZONE="${ZONE:-us-central1-a}"
export INSTANCE_NAME="${INSTANCE_NAME:-ui-test-execution-agent-vm}"
export NETWORK_NAME="${NETWORK_NAME:-agent-network}"
export SUBNET_NAME="${SUBNET_NAME:-agent-network}"
export MACHINE_TYPE="${MACHINE_TYPE:-t2d-standard-2}"
export SERVICE_NAME="${SERVICE_NAME:-ui-test-execution-agent}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"
export NO_VNC_PORT="${NO_VNC_PORT:-6901}"
export VNC_PORT="${VNC_PORT:-5901}"
export AGENT_SERVER_PORT="${AGENT_SERVER_PORT:-443}"
export APP_LOG_FINAL_FOLDER="/app/log"
export VNC_RESOLUTION="${VNC_RESOLUTION:-1920x1080}"
export LOG_LEVEL="${LOG_LEVEL:-INFO}"
export INSTRUCTION_MODEL_NAME="${INSTRUCTION_MODEL_NAME:-meta-llama/llama-4-maverick-17b-128e-instruct}"
export VISION_MODEL_NAME="${VISION_MODEL_NAME:-meta-llama/llama-4-maverick-17b-128e-instruct}"
export INSTRUCTION_MODEL_PROVIDER="${INSTRUCTION_MODEL_PROVIDER:-groq}"
export VISION_MODEL_PROVIDER="${VISION_MODEL_PROVIDER:-groq}"
export UNATTENDED_MODE="${UNATTENDED_MODE:-false}"
export DEBUG_MODE="${DEBUG_MODE:-true}"
export JAVA_APP_STARTUP_SCRIPT="${JAVA_APP_STARTUP_SCRIPT:-/app/java_app_startup.sh}"

# --- Additional Configuration ---
export GCP_SERVICES="${GCP_SERVICES:-compute.googleapis.com containerregistry.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com}"
export FIREWALL_NOVNC_SOURCE_RANGES="${FIREWALL_NOVNC_SOURCE_RANGES:-0.0.0.0/0}"
export FIREWALL_APP_INTERNAL_PORTS="${FIREWALL_APP_INTERNAL_PORTS:-8000-8100}"
export FIREWALL_APP_INTERNAL_SOURCE_RANGES="${FIREWALL_APP_INTERNAL_SOURCE_RANGES:-10.128.0.0/9}"
export FIREWALL_AGENT_SERVER_SOURCE_RANGES="${FIREWALL_AGENT_SERVER_SOURCE_RANGES:-0.0.0.0/0}"
export FIREWALL_SSH_PORT="${FIREWALL_SSH_PORT:-22}"
export FIREWALL_SSH_SOURCE_RANGES="${FIREWALL_SSH_SOURCE_RANGES:-35.235.240.0/20}"
export GCE_IMAGE="${GCE_IMAGE:-projects/cos-cloud/global/images/cos-121-18867-90-97}"
export BOOT_DISK_SIZE="${BOOT_DISK_SIZE:-50GB}"
export BOOT_DISK_TYPE="${BOOT_DISK_TYPE:-pd-balanced}"
export GRACEFUL_SHUTDOWN_DURATION="${GRACEFUL_SHUTDOWN_DURATION:-1m}"
export INSTANCE_STATUS_CHECK_INTERVAL="${INSTANCE_STATUS_CHECK_INTERVAL:-5}"
export SECRET_REPLICATION_POLICY="${SECRET_REPLICATION_POLICY:-automatic}"
export NETWORK_SUBNET_MODE="${NETWORK_SUBNET_MODE:-auto}"
export NETWORK_MTU="${NETWORK_MTU:-1460}"
export NETWORK_BGP_ROUTING_MODE="${NETWORK_BGP_ROUTING_MODE:-regional}"
export DEFAULT_VNC_PASSWORD="${DEFAULT_VNC_PASSWORD:-123456}"
export CONTAINER_VM_LABEL="${CONTAINER_VM_LABEL:-cos-121-18867-90-97}"
export SECRET_ACCESSOR_ROLE="${SECRET_ACCESSOR_ROLE:-roles/secretmanager.secretAccessor}"
export CLOUD_PLATFORM_SCOPE="${CLOUD_PLATFORM_SCOPE:-https://www.googleapis.com/auth/cloud-platform}"
export PROVISIONING_MODEL="${PROVISIONING_MODEL:-SPOT}"
export INSTANCE_TERMINATION_ACTION="${INSTANCE_TERMINATION_ACTION:-STOP}"

# --- Prerequisites ---
echo "Step 1: Enabling necessary GCP services..."
gcloud services enable ${GCP_SERVICES} --project=${PROJECT_ID}

# --- Secret Management (IMPORTANT) ---
echo "Step 2: Setting up secrets in Google Secret Manager..."
echo "Please ensure you have created the following secrets in Secret Manager:"
echo " - GROQ_API_KEY"
echo " - GROQ_ENDPOINT"
echo " - VECTOR_DB_URL"
echo " - VNC_PW"

# --- Networking ---
echo "Step 3: Setting up VPC network and firewall rules..."

if ! gcloud compute networks describe ${NETWORK_NAME} --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating VPC network '${NETWORK_NAME}'..."
    gcloud compute networks create ${NETWORK_NAME} --subnet-mode=${NETWORK_SUBNET_MODE} --mtu=${NETWORK_MTU} --bgp-routing-mode=${NETWORK_BGP_ROUTING_MODE} --project=${PROJECT_ID}
else
    echo "VPC network '${NETWORK_NAME}' already exists."
fi

if ! gcloud compute firewall-rules describe allow-novnc --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-novnc' for port ${NO_VNC_PORT}..."
    gcloud compute firewall-rules create allow-novnc --network=${NETWORK_NAME} --allow=tcp:${NO_VNC_PORT} --source-ranges=${FIREWALL_NOVNC_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-novnc' already exists."
fi

if ! gcloud compute firewall-rules describe allow-app-internal --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-app-internal'..."
    gcloud compute firewall-rules create allow-app-internal --network=${NETWORK_NAME} --allow=tcp:${FIREWALL_APP_INTERNAL_PORTS} --source-ranges=${FIREWALL_APP_INTERNAL_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-app-internal' already exists."
fi

if ! gcloud compute firewall-rules describe allow-agent-server --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-agent-server' for port ${AGENT_SERVER_PORT}..."
    gcloud compute firewall-rules create allow-agent-server --network=${NETWORK_NAME} --allow=tcp:${AGENT_SERVER_PORT} --source-ranges=${FIREWALL_AGENT_SERVER_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-agent-server' already exists."
fi

if ! gcloud compute firewall-rules describe allow-ssh --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-ssh'..."
    gcloud compute firewall-rules create allow-ssh --network=${NETWORK_NAME} --allow=tcp:${FIREWALL_SSH_PORT} --source-ranges=${FIREWALL_SSH_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-ssh' already exists."
fi

# --- Deploy GCE VM ---
echo "Step 4: Creating GCE instance and deploying the container..."

# Delete the instance if it exists
if gcloud compute instances describe ${INSTANCE_NAME} --project=${PROJECT_ID} --zone=${ZONE} &>/dev/null; then
    echo "Instance '${INSTANCE_NAME}' found. Deleting it..."
    gcloud compute instances delete ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --quiet
    echo "Instance '${INSTANCE_NAME}' deleted."
fi

# Manage secrets access
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
    --role="${SECRET_ACCESSOR_ROLE}"

# Create new instance
gcloud beta compute instances create ${INSTANCE_NAME} \
    --project=${PROJECT_ID} \
    --zone=${ZONE} \
    --machine-type=${MACHINE_TYPE} \
    --network-interface=network-tier=STANDARD,subnet=${SUBNET_NAME} \
    --provisioning-model=${PROVISIONING_MODEL} \
    --instance-termination-action=${INSTANCE_TERMINATION_ACTION} \
    --service-account=$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com \
    --scopes=${CLOUD_PLATFORM_SCOPE} \
    --image=${GCE_IMAGE} \
    --boot-disk-size=${BOOT_DISK_SIZE} \
    --boot-disk-type=${BOOT_DISK_TYPE} \
    --boot-disk-device-name=${INSTANCE_NAME} \
    --graceful-shutdown \
    --graceful-shutdown-max-duration=${GRACEFUL_SHUTDOWN_DURATION} \
    --metadata-from-file=startup-script=agent_startup_script.sh \
    --metadata=gcp-project-id=${PROJECT_ID},gcp-service-name=${SERVICE_NAME},gcp-image-tag=${IMAGE_TAG},no-vnc-port=${NO_VNC_PORT},vnc-port=${VNC_PORT},agent-server-port=${AGENT_SERVER_PORT},app-final-log-folder=${APP_LOG_FINAL_FOLDER},VNC_RESOLUTION=${VNC_RESOLUTION},LOG_LEVEL=${LOG_LEVEL},INSTRUCTION_MODEL_NAME=${INSTRUCTION_MODEL_NAME},VISION_MODEL_NAME=${VISION_MODEL_NAME},VISION_MODEL_PROVIDER=${VISION_MODEL_PROVIDER},INSTRUCTION_MODEL_PROVIDER=${INSTRUCTION_MODEL_PROVIDER},UNATTENDED_MODE=${UNATTENDED_MODE},DEBUG_MODE=${DEBUG_MODE},java-app-startup-script=${JAVA_APP_STARTUP_SCRIPT} \
    --labels=container-vm=${CONTAINER_VM_LABEL}

echo "Waiting for instance ${INSTANCE_NAME} to be running..."
while [[ $(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(status)') != "RUNNING" ]]; do
  echo -n "."
  sleep ${INSTANCE_STATUS_CHECK_INTERVAL}
done
echo "Instance is running."

echo "Fetching instance details..."
EXTERNAL_IP=$(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(networkInterfaces[0].accessConfigs[0].natIP)')

echo "--- Deployment Summary ---"
echo "Agent VM '${INSTANCE_NAME}' created."
echo "Agent is running on ${AGENT_SERVER_PORT} port."
echo "In order to get the internal Agent host name, execute the following command inside the VM: 'curl \"http://metadata.google.internal/computeMetadata/v1/instance/hostname\" -H \"Metadata-Flavor: Google\"'"
echo "To access the Agent via noVNC, connect to https://${EXTERNAL_IP}:${NO_VNC_PORT}/vnc.html"
echo "It may take a few minutes for the VM to start and agent to be available."