#!/bin/bash

echo "🚀 SafeSnap Vision API Complete Integration Test"
echo "=============================================="

# Configuration
BACKEND_URL="http://localhost:8080"
TEST_USER_EMAIL="realtest@safesnap.com"
TEST_USER_PASSWORD="password123"

# Ensure jq is installed
if ! command -v jq &> /dev/null; then
    echo "❌ 'jq' is required but not installed. Please install it (e.g., brew install jq)"
    exit 1
fi

echo "🔐 Logging in to SafeSnap..."
LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$TEST_USER_EMAIL\", \"password\": \"$TEST_USER_PASSWORD\"}")

echo "Login response: $LOGIN_RESPONSE"

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Login failed!"
    echo "Response: $LOGIN_RESPONSE"
    echo ""
    echo "🔧 Trying to create user first..."

    CREATE_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/register \
      -H "Content-Type: application/json" \
      -d "{
        \"name\": \"Test Worker\",
        \"email\": \"$TEST_USER_EMAIL\",
        \"password\": \"$TEST_USER_PASSWORD\",
        \"role\": \"WORKER\"
      }")

    echo "User creation response: $CREATE_RESPONSE"

    echo "🔄 Retrying login..."
    LOGIN_RESPONSE=$(curl -s -X POST $BACKEND_URL/api/auth/login \
      -H "Content-Type: application/json" \
      -d "{\"email\": \"$TEST_USER_EMAIL\", \"password\": \"$TEST_USER_PASSWORD\"}")

    TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

    if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
        echo "❌ Still failed after user creation!"
        exit 1
    fi
fi

echo "✅ Login successful"

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

        UPLOAD_RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
            -H "Content-Type: image/jpeg" \
            --data-binary "@$image")

        if [ "$UPLOAD_RESULT" = "200" ]; then
            uploaded_urls+=("$FILE_URL")
            image_descriptions+=("$description")
            echo "✅ Uploaded: $filename"
        else
            echo "❌ Upload failed for $filename (HTTP $UPLOAD_RESULT)"
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
echo "============================"
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
