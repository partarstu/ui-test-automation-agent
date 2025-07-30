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
export INSTANCE_NAME="${INSTANCE_NAME:-ui-test-automation-agent-vm}"
export NETWORK_NAME="${NETWORK_NAME:-agent-network}"
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
export MODEL_PROVIDER="${MODEL_PROVIDER:-groq}"
export UNATTENDED_MODE="${UNATTENDED_MODE:-false}"
export DEBUG_MODE="${DEBUG_MODE:-true}"
export JAVA_APP_STARTUP_SCRIPT="${JAVA_APP_STARTUP_SCRIPT:-/app/java_app_startup.sh}"

# --- Prerequisites ---
echo "Step 1: Enabling necessary GCP services..."
gcloud services enable compute.googleapis.com containerregistry.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com --project=${PROJECT_ID}

# --- Secret Management (IMPORTANT) ---
echo "Step 2: Setting up secrets in Google Secret Manager..."
echo "Please ensure you have created the following secrets in Secret Manager:"
echo " - GROQ_API_KEY"
echo " - GROQ_ENDPOINT"
echo " - VECTOR_DB_URL"
echo " - VNC_PW"
echo "You can create them with the following commands (replace values):"
echo "gcloud secrets create GROQ_API_KEY --replication-policy=\"automatic\" --project=${PROJECT_ID}"
echo "echo -n \"your-groq-api-key\" | gcloud secrets versions add GROQ_API_KEY --data-file=- --project=${PROJECT_ID}"
# Repeat for GROQ_ENDPOINT and VECTOR_DB_URL
echo "gcloud secrets create VNC_PW --replication-policy=\"automatic\" --project=${PROJECT_ID}"
echo "echo -n \"123456\" | gcloud secrets versions add VNC_PW --data-file=- --project=${PROJECT_ID}"

# --- Networking ---
echo "Step 3: Setting up VPC network and firewall rules..."

if ! gcloud compute networks describe ${NETWORK_NAME} --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating VPC network '${NETWORK_NAME}'..."
    gcloud compute networks create ${NETWORK_NAME} --subnet-mode=auto --mtu=1460 --bgp-routing-mode=regional --project=${PROJECT_ID}
else
    echo "VPC network '${NETWORK_NAME}' already exists."
fi

if ! gcloud compute firewall-rules describe allow-novnc --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-novnc' for port ${NO_VNC_PORT}..."
    gcloud compute firewall-rules create allow-novnc --network=${NETWORK_NAME} --allow=tcp:${NO_VNC_PORT} --source-ranges=0.0.0.0/0 --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-novnc' already exists."
fi

if ! gcloud compute firewall-rules describe allow-app-internal --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-app-internal'..."
    gcloud compute firewall-rules create allow-app-internal --network=${NETWORK_NAME} --allow=tcp:8000-8100 --source-ranges=10.128.0.0/9 --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-app-internal' already exists."
fi

if ! gcloud compute firewall-rules describe allow-ssh --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-ssh'..."
    gcloud compute firewall-rules create allow-ssh --network=${NETWORK_NAME} --allow=tcp:22 --source-ranges=35.235.240.0/20 --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-ssh' already exists."
fi

# --- Build Docker Image ---
echo "Step 4: Building and pushing the Docker image using Cloud Build..."
pushd ../..
gcloud builds submit . --config deployment/cloud/cloudbuild_gce.yaml --substitutions=_SERVICE_NAME=${SERVICE_NAME},_IMAGE_TAG=${IMAGE_TAG} --project=${PROJECT_ID}
popd

# --- Deploy GCE VM ---
echo "Step 5: Creating GCE instance and deploying the container..."

# Delete the instance if it exists
if gcloud compute instances describe ${INSTANCE_NAME} --project=${PROJECT_ID} --zone=${ZONE} &>/dev/null; then
    echo "Instance '${INSTANCE_NAME}' found. Deleting it..."
    gcloud compute instances delete ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --quiet
    echo "Instance '${INSTANCE_NAME}' deleted."
fi

# Manage secrets access
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"

# Create new instance
gcloud beta compute instances create ${INSTANCE_NAME} \
    --project=${PROJECT_ID} \
    --zone=${ZONE} \
    --machine-type=${MACHINE_TYPE} \
    --network-interface=network-tier=STANDARD,subnet=${NETWORK_NAME} \
    --provisioning-model=SPOT \
    --instance-termination-action=STOP \
    --service-account=$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com \
    --scopes=https://www.googleapis.com/auth/cloud-platform \
    --image=projects/cos-cloud/global/images/cos-121-18867-90-97 \
    --boot-disk-size=50GB \
    --boot-disk-type=pd-balanced \
    --boot-disk-device-name=${INSTANCE_NAME} \
    --graceful-shutdown \
    --graceful-shutdown-max-duration=1m \
    --metadata-from-file=startup-script=gce_startup_script.sh \
    --metadata=gcp-project-id=${PROJECT_ID},gcp-service-name=${SERVICE_NAME},gcp-image-tag=${IMAGE_TAG},no-vnc-port=${NO_VNC_PORT},vnc-port=${VNC_PORT},agent-server-port=${AGENT_SERVER_PORT},app-final-log-folder=${APP_LOG_FINAL_FOLDER},VNC_RESOLUTION=${VNC_RESOLUTION},LOG_LEVEL=${LOG_LEVEL},INSTRUCTION_MODEL_NAME=${INSTRUCTION_MODEL_NAME},VISION_MODEL_NAME=${VISION_MODEL_NAME},MODEL_PROVIDER=${MODEL_PROVIDER},UNATTENDED_MODE=${UNATTENDED_MODE},DEBUG_MODE=${DEBUG_MODE},java-app-startup-script=${JAVA_APP_STARTUP_SCRIPT} \
    --labels=container-vm=cos-121-18867-90-97

echo "Waiting for instance ${INSTANCE_NAME} to be running..."
while [[ $(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(status)') != "RUNNING" ]]; do
  echo -n "."
  sleep 5
done
echo "Instance is running."

echo "Fetching instance details..."
EXTERNAL_IP=$(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(networkInterfaces[0].accessConfigs[0].natIP)')

echo "--- Deployment Summary ---"
echo "Agent VM '${INSTANCE_NAME}' created."
echo "Agent is running on ${AGENT_SERVER_PORT} port."
echo "In order to get the internal Agent host name, execute the following command inside the VM: 'curl \"http://metadata.google.internal/computeMetadata/v1/instance/hostname\" -H \"Metadata-Flavor: Google\"'"
echo "To access the Agent via noVNC, connect to https://${EXTERNAL_IP}:${NO_VNC_PORT}"
echo "It may take a few minutes for the VM to start and agent to be available."