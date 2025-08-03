#!/bin/bash

set -e

echo "🚀 SafeSnap Vision API E2E Test"
echo "==============================="

BACKEND_URL="http://localhost:8080"
WORKER_EMAIL="integration-worker@safesnap.test"
WORKER_PASSWORD="IntegrationTest123!"
MANAGER_EMAIL="integration-manager@safesnap.test"
MANAGER_PASSWORD="IntegrationTest123!"

require_jq() {
  if ! command -v jq &> /dev/null; then
    echo "❌ 'jq' not found. Install it to run this script."
    exit 1
  fi
}

login_user() {
  local email=$1
  local password=$2
  curl -s -X POST "$BACKEND_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"$email\", \"password\": \"$password\"}" | jq -r '.token'
}

upload_images() {
  local token=$1
  local urls=""
  
  # Use only 3 specific test images
  declare -a test_images=(
    "test-images/construction-worker-1.jpg"
    "test-images/hard-hat-safety.jpg"
    "test-images/worker-without-helmet.jpg"
  )
  
  for path in "${test_images[@]}"; do
    if [ -f "$path" ]; then
      ext="${path##*.}"
      res=$(curl -s -X POST "$BACKEND_URL/api/s3/upload-url" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"fileType\":\"IMAGE\",\"fileExtension\":\"$ext\"}")
      uploadUrl=$(echo "$res" | jq -r '.uploadUrl')
      s3Url=$(echo "$res" | jq -r '.s3Url')
      status=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$uploadUrl" \
        -H "Content-Type: image/jpeg" --data-binary "@$path")
      
      if [ "$status" = "200" ]; then
        urls="$urls $s3Url"
        echo "✅ Uploaded $path (status: $status)" >&2
      else
        echo "❌ Upload failed for $path (status: $status)" >&2
      fi
    else
      echo "⚠️ Missing file: $path" >&2
    fi
  done
  echo "$urls"
}

create_incident() {
  local token=$1
  local urls_str="$2"
  local urls_json=$(echo "$urls_str" | tr ' ' '\n' | jq -R . | jq -s .)
  payload=$(jq -n \
    --arg title "Vision E2E Test" \
    --arg description "Testing Vision API and RCA workflow" \
    --arg severity "HIGH" \
    --argjson latitude 42.36 \
    --argjson longitude -71.05 \
    --arg locationDescription "E2E Test Location" \
    --argjson imageUrls "$urls_json" \
    '{title: $title, description: $description, severity: $severity, latitude: $latitude, longitude: $longitude, locationDescription: $locationDescription, imageUrls: $imageUrls, audioUrls: []}')
  curl -s -X POST "$BACKEND_URL/api/incidents" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" | jq -r '.id'
}

wait_for_rca() {
  local token=$1
  local incidentId=$2
  local max_wait=30
  local elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    result=$(curl -s -X GET "$BACKEND_URL/api/incidents/$incidentId/rca/suggestions" \
      -H "Authorization: Bearer $token")
    # Check if result has an id field (indicating a valid suggestion)
    hasId=$(echo "$result" | jq -r '.id' 2>/dev/null || echo "")
    if [ "$hasId" != "null" ] && [ "$hasId" != "" ]; then
      echo "✅ RCA suggestions ready after $elapsed seconds"
      echo "$result" | jq .
      return
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "❌ RCA suggestions did not generate in time"
  exit 1
}

approve_rca() {
  local token=$1
  local incidentId=$2
  
  # First, get the AI suggestions to use as base content
  echo "📋 Getting AI suggestions for approval..."
  suggestions=$(curl -s -X GET "$BACKEND_URL/api/incidents/$incidentId/rca/suggestions" \
    -H "Authorization: Bearer $token")
  
  # Extract the AI-generated content
  fiveWhys=$(echo "$suggestions" | jq -r '.suggestedFiveWhys')
  correctiveAction=$(echo "$suggestions" | jq -r '.suggestedCorrectiveAction')
  preventiveAction=$(echo "$suggestions" | jq -r '.suggestedPreventiveAction')
  
  # Mark as reviewed first
  curl -s -X POST "$BACKEND_URL/api/incidents/$incidentId/rca/suggestions/review" \
    -H "Authorization: Bearer $token"
  
  # Create RCA with AI-generated content (manager can modify if needed)
  payload=$(jq -n \
    --arg fiveWhys "$fiveWhys" \
    --arg correctiveAction "$correctiveAction" \
    --arg preventiveAction "$preventiveAction" \
    '{fiveWhys: $fiveWhys, correctiveAction: $correctiveAction, preventiveAction: $preventiveAction}')
  
  curl -s -X POST "$BACKEND_URL/api/incidents/$incidentId/rca/approve" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" | jq .
}

verify_rca_linked() {
  local token=$1
  local incidentId=$2
  echo "📄 Fetching incident to inspect RCA..."
  response=$(curl -s -H "Authorization: Bearer $token" \
    "$BACKEND_URL/api/incidents/$incidentId")

  hasRca=$(echo "$response" | jq '.rcaReport != null')
  if [ "$hasRca" != "true" ]; then
    echo "❌ RCA is missing in incident payload!"
    exit 1
  fi

  echo "✅ RCA linked successfully. Full RCA content:"
  echo "$response" | jq '.rcaReport'
}

### MAIN FLOW
require_jq

echo "🔐 Logging in as worker..."
WORKER_TOKEN=$(login_user "$WORKER_EMAIL" "$WORKER_PASSWORD")

echo "📤 Uploading images..."
UPLOADED_URLS=$(upload_images "$WORKER_TOKEN")

echo "📝 Creating incident..."
INCIDENT_ID=$(create_incident "$WORKER_TOKEN" "$UPLOADED_URLS")
echo "✅ Incident created: $INCIDENT_ID"

echo "🔐 Logging in as manager..."
MANAGER_TOKEN=$(login_user "$MANAGER_EMAIL" "$MANAGER_PASSWORD")

echo "⏳ Waiting for RCA generation..."
wait_for_rca "$MANAGER_TOKEN" "$INCIDENT_ID"

echo "📝 Reviewing and approving RCA..."
approve_rca "$MANAGER_TOKEN" "$INCIDENT_ID"

echo "🔍 Verifying RCA is linked to incident..."
verify_rca_linked "$MANAGER_TOKEN" "$INCIDENT_ID"

echo "🎉 E2E test completed successfully!"
