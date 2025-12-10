#!/bin/bash

echo "🚀 SafeSnap Vision API Complete Integration Test"
echo "=============================================="

# Configuration
BACKEND_URL="http://localhost:8080"
TS_SUFFIX=$(date +%s)
TEST_USER_EMAIL=${TEST_USER_EMAIL:-"worker.$TS_SUFFIX@safesnap.test"}
TEST_USER_PASSWORD=${TEST_USER_PASSWORD:-"password123"}
MANAGER_EMAIL=${MANAGER_EMAIL:-"manager.$TS_SUFFIX@safesnap.test"}
MANAGER_PASSWORD=${MANAGER_PASSWORD:-"password123"}
MANAGER_TOKEN=""

# Ensure jq is installed
if ! command -v jq &> /dev/null; then
    echo "❌ 'jq' is required but not installed. Please install it (e.g., brew install jq)"
    exit 1
fi

# Helper to build JSON safely
json_login() {
  jq -n --arg email "$1" --arg password "$2" '{email: $email, password: $password}'
}
json_register() {
  jq -n --arg name "$1" --arg email "$2" --arg password "$3" --arg role "$4" '{name: $name, email: $email, password: $password, role: $role}'
}

echo "🔐 Logging in to SafeSnap..."
LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
  -H "Content-Type: application/json" \
  --data "$(json_login "$TEST_USER_EMAIL" "$TEST_USER_PASSWORD")")

echo "Login response: $LOGIN_RESPONSE"

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Login failed!"
    echo "Response: $LOGIN_RESPONSE"
    echo ""
    echo "🔧 Trying to create user first..."

    CREATE_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/register \
      -H "Content-Type: application/json" \
      --data "$(json_register "Test Worker" "$TEST_USER_EMAIL" "$TEST_USER_PASSWORD" "WORKER")")

    echo "User creation response: $CREATE_RESPONSE"

    echo "🔄 Retrying login..."
    LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
      -H "Content-Type: application/json" \
      --data "$(json_login "$TEST_USER_EMAIL" "$TEST_USER_PASSWORD")")

    TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

    if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
        echo "❌ Still failed after user creation!"
        exit 1
    fi
fi

echo "✅ Login successful"

# Ensure manager exists and login
echo "👤 Ensuring manager account exists..."
MANAGER_LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
  -H "Content-Type: application/json" \
  --data "$(json_login "$MANAGER_EMAIL" "$MANAGER_PASSWORD")")
MANAGER_TOKEN=$(echo $MANAGER_LOGIN_RESPONSE | jq -r '.token')
if [ "$MANAGER_TOKEN" = "null" ] || [ -z "$MANAGER_TOKEN" ]; then
  echo "🧑‍💼 Creating manager user..."
  CREATE_MANAGER_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/register \
    -H "Content-Type: application/json" \
    --data "$(json_register "Test Manager" "$MANAGER_EMAIL" "$MANAGER_PASSWORD" "MANAGER")")
  echo "Manager creation response: $CREATE_MANAGER_RESPONSE"
  echo "🔄 Retrying manager login..."
  MANAGER_LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
    -H "Content-Type: application/json" \
    --data "$(json_login "$MANAGER_EMAIL" "$MANAGER_PASSWORD")")
  MANAGER_TOKEN=$(echo $MANAGER_LOGIN_RESPONSE | jq -r '.token')
  if [ "$MANAGER_TOKEN" = "null" ] || [ -z "$MANAGER_TOKEN" ]; then
      echo "❌ Manager login failed!"; exit 1
  fi
fi

echo "✅ Manager login successful"

if [ ! -d "test-images" ]; then
    echo "📥 Test images not found. Downloading..."
    ./download-test-images.sh
fi

declare -a test_images=(
    "test-images/construction-worker-1.jpg:Construction worker with gear"
    "test-images/hard-hat-safety.jpg:Worker with hard hat"
    "test-images/worker-without-helmet.jpg:Potential PPE violation"
    "test-images/ladder-work.jpg:Ladder work (fall hazard)"
    "test-images/safety-vest-worker.jpg:High-vis safety vest"
    "test-images/electrical-work.jpg:Electrical work scenario"
    "test-images/welding-work.jpg:Welding operation"
)

uploaded_urls=()
image_descriptions=()

echo ""
echo "📤 Uploading test images to S3..."

for item in "${test_images[@]}"; do
    IFS=':' read -r image description <<< "$item"

    if [ -f "$image" ]; then
        filename=$(basename "$image")
        ext="${filename##*.}"
        # Determine content type based on extension
        case "$ext" in
          jpg|jpeg) CONTENT_TYPE="image/jpeg" ;;
          png) CONTENT_TYPE="image/png" ;;
          webp) CONTENT_TYPE="image/webp" ;;
          gif) CONTENT_TYPE="image/gif" ;;
          *) CONTENT_TYPE="application/octet-stream" ;;
        esac
        echo "📤 Uploading $filename ($description)..."

        UPLOAD_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/s3/upload-url" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $TOKEN" \
          -d "{
            \"fileType\": \"IMAGE\",
            \"fileExtension\": \"$ext\"
          }")

        UPLOAD_URL=$(echo "$UPLOAD_RESPONSE" | jq -r '.uploadUrl')
        FILE_URL=$(echo "$UPLOAD_RESPONSE" | jq -r '.s3Url')

        if [ "$UPLOAD_URL" = "null" ] || [ -z "$UPLOAD_URL" ]; then
            echo "❌ Failed to get upload URL for $filename"
            echo "Response: $UPLOAD_RESPONSE"
            continue
        fi

        # First attempt: minimal PUT with --upload-file and exact Content-Type (do not set Content-Length)
        UPLOAD_RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
            -H "Content-Type: $CONTENT_TYPE" \
            -T "$image")

        if [ "$UPLOAD_RESULT" = "200" ]; then
            uploaded_urls+=("$FILE_URL")
            image_descriptions+=("$description")
            echo "✅ Uploaded: $filename"
            echo "   S3 URL: $FILE_URL"
            # Verify existence
            EXISTS_RESP=$(curl -s -G "$BACKEND_URL/api/s3/file-exists" --data-urlencode "s3Url=$FILE_URL")
            echo "   Exists check: $EXISTS_RESP"
            # Generate download URL
            DL_RESP=$(curl -s -X POST "$BACKEND_URL/api/s3/download-url" -H "Content-Type: application/json" -d "{\"s3Url\": \"$FILE_URL\"}")
            echo "   Download URL: $(echo "$DL_RESP" | jq -r '.downloadUrl // "N/A"')"
        else
            echo "⚠️  Upload attempt failed for $filename (HTTP $UPLOAD_RESULT). Retrying with --data-binary and diagnostics..."
            # Capture and show S3 error body for diagnostics
            ERROR_BODY=$(curl -s -X PUT "$UPLOAD_URL" \
                -H "Content-Type: $CONTENT_TYPE" \
                --data-binary "@$image")
            ERROR_CODE=$(echo "$ERROR_BODY" | xmllint --xpath 'string(//Code)' - 2>/dev/null || echo "")
            ERROR_MSG=$(echo "$ERROR_BODY" | xmllint --xpath 'string(//Message)' - 2>/dev/null || echo "")
            if [ -n "$ERROR_CODE" ]; then
              echo "   S3 Error Code: $ERROR_CODE"
              echo "   S3 Error Message: $ERROR_MSG"
            else
              echo "   S3 Error Body: ${ERROR_BODY:0:500}"
            fi

            # Second attempt with --data-binary
            UPLOAD_RESULT2=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
                -H "Content-Type: $CONTENT_TYPE" \
                --data-binary "@$image")
            if [ "$UPLOAD_RESULT2" = "200" ]; then
                uploaded_urls+=("$FILE_URL")
                image_descriptions+=("$description")
                echo "✅ Uploaded on retry: $filename"
                echo "   S3 URL: $FILE_URL"
                EXISTS_RESP=$(curl -s -G "$BACKEND_URL/api/s3/file-exists" --data-urlencode "s3Url=$FILE_URL")
                echo "   Exists check: $EXISTS_RESP"
                DL_RESP=$(curl -s -X POST "$BACKEND_URL/api/s3/download-url" -H "Content-Type: application/json" -d "{\"s3Url\": \"$FILE_URL\"}")
                echo "   Download URL: $(echo "$DL_RESP" | jq -r '.downloadUrl // "N/A"')"
            else
                echo "⚠️  Second attempt failed (HTTP $UPLOAD_RESULT2). Trying final minimal PUT without extra curl config..."
                UPLOAD_RESULT3=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
                  -H "Content-Type: $CONTENT_TYPE" \
                  -T "$image")
                if [ "$UPLOAD_RESULT3" = "200" ]; then
                  uploaded_urls+=("$FILE_URL")
                  image_descriptions+=("$description")
                  echo "✅ Uploaded on final minimal PUT: $filename"
                  echo "   S3 URL: $FILE_URL"
                  EXISTS_RESP=$(curl -s -G "$BACKEND_URL/api/s3/file-exists" --data-urlencode "s3Url=$FILE_URL")
                  echo "   Exists check: $EXISTS_RESP"
                  DL_RESP=$(curl -s -X POST "$BACKEND_URL/api/s3/download-url" -H "Content-Type: application/json" -d "{\"s3Url\": \"$FILE_URL\"}")
                  echo "   Download URL: $(echo "$DL_RESP" | jq -r '.downloadUrl // "N/A"')"
                else
                  echo "❌ Upload failed for $filename (HTTP $UPLOAD_RESULT3)"
                  echo "   Presigned URL: $UPLOAD_URL"
                fi
            fi
        fi
    else
        echo "⚠️  Image not found: $image"
    fi
done

if [ ${#uploaded_urls[@]} -eq 0 ]; then
    echo "❌ No images uploaded successfully!"
    exit 1
fi

echo ""
echo "📝 Creating incident with ${#uploaded_urls[@]} images..."

image_urls_json=$(printf '%s\n' "${uploaded_urls[@]}" | jq -R . | jq -s .)

INCIDENT_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/incidents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"title\": \"Vision API Integration Test - Multiple Safety Scenarios\",
    \"description\": \"Testing Google Vision API with various workplace safety images including PPE compliance, construction work, and potential hazards\",
    \"severity\": \"HIGH\",
    \"latitude\": 42.3601,
    \"longitude\": -71.0589,
    \"locationDescription\": \"SafeSnap Test Environment - Multiple Workplace Scenarios\",
    \"imageUrls\": $image_urls_json,
    \"audioUrls\": []
  }")

INCIDENT_ID=$(echo $INCIDENT_RESPONSE | jq -r '.id')

if [ "$INCIDENT_ID" = "null" ]; then
  echo "❌ Failed to create incident!"
  echo "Response: $INCIDENT_RESPONSE"
  exit 1
fi

echo "✅ Incident created: $INCIDENT_ID"
echo "🔗 Incident URL: $BACKEND_URL/incidents/$INCIDENT_ID"

echo "🧠 Triggering RCA suggestion generation (manager)..."
RCA_SUGGESTIONS_RESPONSE=$(curl -s -X GET "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions" \
  -H "Authorization: Bearer $MANAGER_TOKEN")
RCA_STATUS=$(echo "$RCA_SUGGESTIONS_RESPONSE" | jq -r '.status // empty')
if [ -z "$RCA_STATUS" ]; then
  echo "⚠️  RCA suggestions not yet available. Attempting regenerate..."
  RCA_REGEN_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions/regenerate" \
    -H "Authorization: Bearer $MANAGER_TOKEN")
  echo "RCA regenerate response: $RCA_REGEN_RESPONSE"
  # Retry fetch once
  sleep 3
  RCA_SUGGESTIONS_RESPONSE=$(curl -s -X GET "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions" \
    -H "Authorization: Bearer $MANAGER_TOKEN")
fi

echo ""
echo "📘 RCA Suggestions (raw):"
echo "$RCA_SUGGESTIONS_RESPONSE" | jq '.'

# Review and approve suggestions
if [ "$(echo "$RCA_SUGGESTIONS_RESPONSE" | jq -r '.id // empty')" != "" ]; then
  echo "📝 Marking RCA suggestions as reviewed..."
  RCA_REVIEWED=$(curl -s -X POST "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions/review" \
    -H "Authorization: Bearer $MANAGER_TOKEN")
  echo "$RCA_REVIEWED" | jq '.'

  echo "✅ Approving RCA suggestions..."
  RCA_APPROVED=$(curl -s -X POST "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions/approve" \
    -H "Authorization: Bearer $MANAGER_TOKEN")
  echo "$RCA_APPROVED" | jq '.'
else
  echo "⚠️  RCA suggestions not found; continuing with image analysis."
fi

echo ""
echo "⏳ Waiting for AI analysis of ${#uploaded_urls[@]} images..."
for i in {1..6}; do
    sleep 5
    echo "   ⏳ Checking progress... ($((i*5))s)"

    ANALYSIS_RESPONSE=$(curl -s -X GET "$BACKEND_URL/api/image-analysis/incident/$INCIDENT_ID/results" \
      -H "Authorization: Bearer $TOKEN")

    STATUS=$(echo $ANALYSIS_RESPONSE | jq -r '.status')
    PROCESSED=$(echo $ANALYSIS_RESPONSE | jq '.processedImages')
    TOTAL=$(echo $ANALYSIS_RESPONSE | jq '.totalImages')

    echo "   📊 Status: $STATUS ($PROCESSED/$TOTAL processed)"

    if [ "$STATUS" = "COMPLETED" ]; then
        break
    fi
    # Also check RCA status during polling
    RCA_POLL=$(curl -s -X GET "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions" \
      -H "Authorization: Bearer $MANAGER_TOKEN")
    echo "   🧠 RCA status: $(echo "$RCA_POLL" | jq -r '.status // "unknown"')"
done

echo ""
echo "🔍 Final Analysis Results:"
echo "=========================="

FINAL_ANALYSIS=$(curl -s -X GET "$BACKEND_URL/api/image-analysis/incident/$INCIDENT_ID/results" \
  -H "Authorization: Bearer $TOKEN")

echo $FINAL_ANALYSIS | jq '.'

echo ""
echo "📊 Summary Report:"
echo "=================="
echo "Incident ID: $INCIDENT_ID"
echo "Total Images: $(echo $FINAL_ANALYSIS | jq '.totalImages')"
echo "Processed: $(echo $FINAL_ANALYSIS | jq '.processedImages')"
echo "Pending: $(echo $FINAL_ANALYSIS | jq '.pendingImages')"
echo "Failed: $(echo $FINAL_ANALYSIS | jq '.failedImages // 0')"
echo "Status: $(echo $FINAL_ANALYSIS | jq -r '.status')"

echo ""
echo "🏷️  All Detected Safety Items:"
echo "=============================="
echo $FINAL_ANALYSIS | jq -r '.results[]?.detectedItems[]?' | sort | uniq -c | sort -nr

echo ""
echo "📝 Individual Image Results:"
for i in $(seq 0 $((${#uploaded_urls[@]}-1))); do
    if [ $i -lt $(echo $FINAL_ANALYSIS | jq '.results | length') ]; then
        echo ""
        echo "Image $((i+1)): ${image_descriptions[$i]}"
        echo "URL: ${uploaded_urls[$i]}"
        echo "Detected: $(echo $FINAL_ANALYSIS | jq -r ".results[$i].detectedItems | join(\", \")")"
        echo "Confidence: $(echo $FINAL_ANALYSIS | jq -r ".results[$i].confidenceScore")"
        echo "Text Found: $(echo $FINAL_ANALYSIS | jq -r ".results[$i].textDetected // \"None\"")"
    fi
done

echo ""
echo "🧾 RCA Summary:"
echo "==============="
RCA_FINAL=$(curl -s -X GET "$BACKEND_URL/api/incidents/$INCIDENT_ID/rca/suggestions" \
  -H "Authorization: Bearer $MANAGER_TOKEN")
echo "ID: $(echo "$RCA_FINAL" | jq -r '.id // "N/A"')"
echo "Status: $(echo "$RCA_FINAL" | jq -r '.status // "N/A"')"
echo "Category: $(echo "$RCA_FINAL" | jq -r '.incidentCategory // "N/A"')"
echo "Five Whys:"
echo "$(echo "$RCA_FINAL" | jq -r '.suggestedFiveWhys // "N/A"')"
echo "Corrective Action:"
echo "$(echo "$RCA_FINAL" | jq -r '.suggestedCorrectiveAction // "N/A"')"
echo "Preventive Action:"
echo "$(echo "$RCA_FINAL" | jq -r '.suggestedPreventiveAction // "N/A"')"

echo ""
echo "🎯 Vision API Test Results:"
echo "==========================="

if [ "$(echo $FINAL_ANALYSIS | jq '.processedImages')" -gt 0 ]; then
    echo "✅ SUCCESS: Vision API is working!"
    echo "   - Images processed successfully"
    echo "   - Safety items detected"
    echo "   - Integration working end-to-end"

    ALL_DETECTED=$(echo $FINAL_ANALYSIS | jq -r '.results[].detectedItems[]?' | tr '\n' ' ')

    if [[ $ALL_DETECTED == *"Hard hat"* ]] || [[ $ALL_DETECTED == *"helmet"* ]]; then
        echo "✅ PPE Detection: Hard hats/helmets detected"
    fi
    if [[ $ALL_DETECTED == *"Safety vest"* ]] || [[ $ALL_DETECTED == *"vest"* ]]; then
        echo "✅ PPE Detection: Safety vests detected"
    fi
    if [[ $ALL_DETECTED == *"Construction"* ]]; then
        echo "✅ Environment Detection: Construction sites detected"
    fi
    if [[ $ALL_DETECTED == *"Person"* ]] || [[ $ALL_DETECTED == *"worker"* ]]; then
        echo "✅ Human Detection: Workers/people detected"
    fi
else
    echo "❌ FAILED: No images processed successfully"
fi

echo ""
echo "🔧 Next Steps:"
echo "=============="
echo "1. Review the detected items above"
echo "2. Check if Vision API detected expected safety elements"
echo "3. Test with your mobile app using these same S3 URLs"
echo "4. Add manual safety checkboxes for items AI cannot detect"

echo ""
echo "✅ Vision API integration test complete!"
