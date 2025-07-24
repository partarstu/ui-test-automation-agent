#!/bin/bash

# Configuration variables
PROJECT_ID="your-gcp-project-id" # Replace with your GCP Project ID
REGION="us-central1" # Replace with your desired region
CHROMA_SERVICE_NAME="chromadb-service" # Replace with your desired service name for Chroma DB
CHROMA_IMAGE="chromadb/chroma:latest" # Official Chroma DB Docker image

echo "Deploying Chroma DB to Google Cloud Run..."

gcloud run deploy ${CHROMA_SERVICE_NAME} \
  --image ${CHROMA_IMAGE} \
  --region ${REGION} \
  --platform managed \
  --allow-unauthenticated \
  --port 8000 \
  --project ${PROJECT_ID}

echo "Chroma DB deployment initiated. Check Google Cloud Run for status."

