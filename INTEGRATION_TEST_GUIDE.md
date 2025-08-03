# 🧪 SafeSnap Full Integration Test Guide

This guide walks you through testing the complete SafeSnap workflow with real OpenAI and Google Vision APIs.

## 🚀 Prerequisites

### **1. Set Environment Variables for Real APIs**
```bash
# OpenAI Configuration
export OPENAI_API_KEY=sk-your-actual-openai-key-here
export OPENAI_MOCK_MODE=false
export OPENAI_ENABLED=true

# Google Vision Configuration
export GOOGLE_VISION_MOCK_MODE=false
export GOOGLE_VISION_ENABLED=true
export GOOGLE_CLOUD_PROJECT_ID=your-project-id
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# Optional: Use development profile
export SPRING_PROFILES_ACTIVE=development
```

### **2. Start SafeSnap Backend**
```bash
# With real APIs enabled
./gradlew bootRun
```

### **3. Verify APIs are Active**
```bash
curl http://localhost:8080/api/rca/health
# Should show: "openAiServiceHealthy": true, "status": "REAL_API"
```

## 📋 **Manual Testing Steps**

### **Step 1: Create Test Users**

#### **Create Worker Account:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Worker",
    "email": "john.worker@company.com",
    "password": "Worker123!",
    "role": "WORKER"
  }'
```

#### **Create Manager Account:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sarah Manager",
    "email": "sarah.manager@company.com", 
    "password": "Manager123!",
    "role": "MANAGER"
  }'
```

**Save the JWT tokens from responses for next steps!**

### **Step 2: Upload Test Image**

#### **Get Pre-signed Upload URL:**
```bash
curl -X GET "http://localhost:8080/api/s3/presigned-url/upload?fileType=IMAGE&fileExtension=jpg" \
  -H "Authorization: Bearer YOUR_WORKER_JWT_TOKEN"
```

#### **Upload Safety Image:**
```bash
# Use the uploadUrl from previous response
curl -X PUT "PRESIGNED_UPLOAD_URL_FROM_RESPONSE" \
  -H "Content-Type: image/jpeg" \
  --data-binary @test-images/construction-worker-1.jpg
```

### **Step 3: Create Safety Incident**

Use the S3 URL from Step 2:

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Authorization: Bearer YOUR_WORKER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Worker fell from scaffolding - PPE violation",
    "description": "John Smith was setting up scaffolding on the north wall when he slipped and fell approximately 8 feet. He was not wearing his required safety harness because he said it was uncomfortable and slowing down his work. The scaffolding planks were wet from morning rain. John sustained minor back injuries and was taken to hospital for evaluation. This incident could have been prevented with proper PPE usage and surface condition management.",
    "severity": "HIGH",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "locationDescription": "Construction site - North wall scaffolding area",
    "imageUrls": ["S3_URL_FROM_STEP_2"]
  }'
```

**Save the incident ID from the response!**

### **Step 4: Monitor Automatic Processing**

#### **Watch Image Analysis Progress:**
```bash
# Check every 30 seconds until imageTags appear
curl -X GET http://localhost:8080/api/incidents/INCIDENT_ID \
  -H "Authorization: Bearer YOUR_WORKER_JWT_TOKEN"

# Look for "imageTags" field in response
```

#### **Watch RCA Generation Progress:**
```bash
# Check every 30 seconds until RCA appears
curl -X GET http://localhost:8080/api/incidents/INCIDENT_ID/rca/suggestions \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"

# Look for status: "GENERATED"
```

### **Step 5: Manager Reviews AI Suggestions**

#### **View AI-Generated RCA:**
```bash
curl -X GET http://localhost:8080/api/incidents/INCIDENT_ID/rca/suggestions \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

**Expected Response:**
```json
{
  "id": "uuid",
  "incidentTitle": "Worker fell from scaffolding - PPE violation",
  "suggestedFiveWhys": "1. Why did this PPE violation occur? Worker was not wearing required safety harness...",
  "suggestedCorrectiveAction": "- Issue replacement harness immediately...",
  "suggestedPreventiveAction": "- Implement mandatory PPE check stations...",
  "incidentCategory": "PPE_VIOLATION",
  "confidenceScore": 0.9,
  "status": "GENERATED",
  "tokensUsed": 450,
  "processingTimeMs": 3200
}
```

#### **Mark as Reviewed:**
```bash
curl -X POST http://localhost:8080/api/incidents/INCIDENT_ID/rca/suggestions/review \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

### **Step 6: Create Final RCA Report**

Manager approves with optional modifications:

```bash
curl -X POST http://localhost:8080/api/incidents/INCIDENT_ID/rca/approve \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fiveWhys": "1. Why did this PPE violation occur? Worker was not wearing required safety harness while working at height\n2. Why was the harness not worn? Worker found it uncomfortable and felt it slowed down work\n3. Why wasn'\''t this prevented by safety protocols? No enforcement of mandatory harness checks at start of shift\n4. Why aren'\''t safety protocols being enforced? Supervisors prioritize productivity over safety compliance\n5. Why isn'\''t there better organizational commitment to PPE? Company culture focuses on speed over safety systems\n\nManager Review: Analysis confirmed through site inspection and worker interview.",
    "correctiveAction": "- Issue properly fitted, comfortable safety harness to worker immediately\n- Conduct mandatory PPE inspection for all workers on site\n- Supervisor to complete safety incident documentation\n- Review and reinforce harness policy with all crew members\n\nAdditional: Site safety briefing scheduled for all workers.",
    "preventiveAction": "- Implement mandatory PPE check stations at work area entrances\n- Provide multiple harness options for proper fit and comfort\n- Train supervisors on safety-first culture and enforcement\n- Review production targets to ensure time for safety protocols\n- Install slip-resistant surfaces on scaffolding\n\nAdditional: Monthly safety harness comfort and fit training program implemented."
  }'
```

### **Step 7: Verify Complete Workflow**

#### **Check Final Incident State:**
```bash
curl -X GET http://localhost:8080/api/incidents/INCIDENT_ID \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

Should show:
- ✅ **imageTags**: Google Vision analysis results
- ✅ **rcaReport**: Final approved RCA report
- ✅ **aiSuggestions**: AI-generated suggestions metadata

#### **Check System Statistics:**
```bash
curl -X GET http://localhost:8080/api/rca/statistics \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

#### **Check Service Health:**
```bash
curl -X GET http://localhost:8080/api/rca/health \
  -H "Authorization: Bearer YOUR_MANAGER_JWT_TOKEN"
```

## 🎯 **Automated Integration Test**

For automated testing, make the script executable and run:

```bash
chmod +x test-integration.sh
./test-integration.sh
```

The script will:
1. ✅ Create test users automatically
2. ✅ Upload test images
3. ✅ Create realistic safety incidents
4. ✅ Wait for AI processing
5. ✅ Complete manager workflow
6. ✅ Verify all integrations
7. ✅ Show detailed results

## 🔍 **Expected Results**

### **Google Vision Analysis:**
Should detect safety-related tags like:
- "Construction site"
- "Hard hat" 
- "Safety vest"
- "Worker"
- "Industrial equipment"

### **OpenAI RCA Generation:**
Should generate professional analysis with:
- **Incident Category**: PPE_VIOLATION (based on description)
- **Five Whys**: Root cause analysis chain
- **Corrective Actions**: Immediate 24-48 hour steps
- **Preventive Actions**: Long-term system improvements
- **Processing Time**: 2-8 seconds
- **Token Usage**: 400-800 tokens

### **Manager Workflow:**
- ✅ View AI suggestions first
- ✅ Review and modify if needed  
- ✅ Approve to create final RCA
- ✅ System tracks if manager modified AI suggestions

## 📊 **Monitoring Integration**

### **Key Metrics to Watch:**
```bash
# API Success Rates
curl http://localhost:8080/actuator/metrics/safesnap.openai.requests

# RCA Generation Stats  
curl http://localhost:8080/actuator/metrics/safesnap.rca.generated

# Processing Times
curl http://localhost:8080/actuator/metrics/safesnap.rca.generation.duration
```

### **Expected Performance:**
- **Image Analysis**: 3-10 seconds
- **RCA Generation**: 2-8 seconds  
- **Total Time**: Incident to RCA ready < 30 seconds
- **Success Rate**: >95%

## 🐛 **Troubleshooting**

### **"RCA generation failed"**
```bash
# Check OpenAI API key
echo $OPENAI_API_KEY

# Check service health
curl http://localhost:8080/api/rca/health

# Check logs
docker logs safesnap-app
```

### **"Image analysis not working"**
```bash
# Check Google credentials
echo $GOOGLE_APPLICATION_CREDENTIALS
ls -la $GOOGLE_APPLICATION_CREDENTIALS

# Test auth
gcloud auth application-default print-access-token
```

### **"Mock mode still enabled"**
```bash
# Verify environment variables
env | grep -E "(OPENAI|GOOGLE_VISION)_MOCK_MODE"

# Check actual configuration
curl http://localhost:8080/actuator/configprops | grep -E "(openai|google)"
```

## 🎉 **Success Criteria**

The integration test passes when:

1. ✅ **Users authenticate** successfully
2. ✅ **Images upload** to S3 and get analyzed by Google Vision
3. ✅ **Incidents trigger** automatic RCA generation
4. ✅ **OpenAI generates** professional RCA suggestions
5. ✅ **Manager can review** and approve RCA
6. ✅ **Final RCA report** is created with manager modifications
7. ✅ **System tracks** all metrics and status correctly
8. ✅ **No mock responses** - all APIs are real

This confirms your complete SafeSnap AI-powered safety system is working end-to-end! 🚀
