# SafeSnap Phase 2 Development Guide

## 🎯 Phase 2 Overview
**Goal:** Complete core incident management functionality for both backend API and mobile app MVP.

## ✅ Phase 1 Completed Features
- [x] JWT authentication system (USER/MANAGER roles)
- [x] Basic entity structure with UUIDs  
- [x] S3 integration with pre-signed URLs
- [x] Google Vision AI service foundation
- [x] Spring Security configuration
- [x] Exception handling and validation

## 🚧 Phase 2 Development Tasks

### Backend Development

#### 1. **Recently Added Components** ✅
- [x] `IncidentController.kt` - Complete CRUD operations
- [x] `IncidentService.kt` - Business logic layer  
- [x] `IncidentRepository.kt` - Database operations with filtering
- [x] Updated `Incident.kt` entity with proper UUID structure
- [x] Enhanced exception handling
- [x] Updated DTOs to match new structure

#### 2. **Next Backend Tasks** 📋
- [ ] Create missing repositories:
  - [ ] `RcaReportRepository.kt`
  - [ ] `AiSuggestionRepository.kt` 
  - [ ] `VoiceTranscriptionRepository.kt`
- [ ] Update entity relationships in remaining entities
- [ ] Create RCA management controller and service
- [ ] Add data validation and business rules
- [ ] Complete image analysis pipeline integration
- [ ] Add incident statistics and reporting endpoints

#### 3. **Testing Tasks** 📋
- [ ] Fix existing user registration conflict in tests
- [ ] Add incident CRUD integration tests
- [ ] Create incident filtering tests
- [ ] Test manager vs user authorization
- [ ] Add S3 file upload integration tests

### Mobile App Development

#### 1. **Project Setup** 📋
```bash
# Create React Native project
npx create-expo-app SafeSnapMobile --template blank-typescript
cd SafeSnapMobile

# Install core dependencies
npm install @react-navigation/native @react-navigation/stack
npm install expo-camera expo-av expo-location expo-file-system
npm install @react-native-async-storage/async-storage
npm install axios @tanstack/react-query
npm install react-hook-form @hookform/resolvers yup
```

#### 2. **Mobile App Structure** 📋
```
SafeSnapMobile/
├── src/
│   ├── components/
│   │   ├── Camera/
│   │   ├── Forms/
│   │   ├── Maps/
│   │   └── UI/
│   ├── screens/
│   │   ├── Auth/
│   │   │   ├── LoginScreen.tsx
│   │   │   └── RegisterScreen.tsx
│   │   ├── Incidents/
│   │   │   ├── CreateIncidentScreen.tsx
│   │   │   ├── IncidentListScreen.tsx
│   │   │   └── IncidentDetailScreen.tsx
│   │   └── Manager/
│   │       └── ManagerDashboardScreen.tsx
│   ├── services/
│   │   ├── api.ts
│   │   ├── auth.ts
│   │   ├── camera.ts
│   │   ├── location.ts
│   │   └── storage.ts
│   ├── types/
│   │   ├── auth.ts
│   │   ├── incident.ts
│   │   └── api.ts
│   └── utils/
│       ├── validation.ts
│       └── constants.ts
└── app.json
```

#### 3. **Core Mobile Features** 📋
- [ ] **Authentication Flow**
  - [ ] Login/Register screens
  - [ ] JWT token storage and management
  - [ ] Auto-login on app start
  - [ ] Role-based navigation

- [ ] **Incident Creation**
  - [ ] Camera integration for photos
  - [ ] Audio recording for voice notes
  - [ ] GPS location capture
  - [ ] Form validation and submission
  - [ ] File upload to S3 via pre-signed URLs

- [ ] **Incident Management**
  - [ ] List incidents (user's own or all for managers)
  - [ ] Incident detail view
  - [ ] Basic search and filtering
  - [ ] Pull-to-refresh functionality

## 🔧 Development Setup

### Backend Setup
```bash
# 1. Start the Spring Boot application
./gradlew bootRun

# 2. Verify authentication works
chmod +x simple-auth-test.sh
./simple-auth-test.sh

# 3. Test incident creation API
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "title": "Test Safety Incident",
    "description": "Worker fell from ladder",
    "severity": "HIGH", 
    "latitude": 42.3601,
    "longitude": -71.0589,
    "locationDescription": "Construction Site A",
    "imageUrls": ["https://s3.amazonaws.com/bucket/image1.jpg"],
    "audioUrls": []
  }'
```

### Mobile App Setup
```bash
# 1. Create mobile project
npx create-expo-app SafeSnapMobile --template blank-typescript
cd SafeSnapMobile

# 2. Install dependencies
npm install @react-navigation/native @react-navigation/stack
npm install expo-camera expo-av expo-location
npm install @react-native-async-storage/async-storage
npm install axios @tanstack/react-query

# 3. Start development server
npx expo start
```

## 📱 Mobile App Architecture

### API Service Layer
```typescript
// src/services/api.ts
export class SafeSnapAPI {
  private baseURL = 'http://localhost:8080/api';
  private token: string | null = null;

  async login(email: string, password: string): Promise<AuthResponse>
  async register(userData: RegisterRequest): Promise<AuthResponse>
  async createIncident(incident: CreateIncidentRequest): Promise<IncidentResponse>
  async getIncidents(filters?: IncidentFilters): Promise<IncidentResponse[]>
  async uploadFile(fileUri: string, fileType: 'IMAGE' | 'AUDIO'): Promise<string>
}
```

### State Management
```typescript
// Using React Query for API state management
const { data: incidents } = useQuery(['incidents'], api.getIncidents);
const createIncidentMutation = useMutation(api.createIncident);
```

## 🚀 Phase 2 Success Criteria

### Backend Success Criteria ✅
- [x] Complete incident CRUD API endpoints
- [x] Role-based authorization working
- [x] S3 file upload integration
- [ ] Image analysis pipeline functional
- [ ] Comprehensive test coverage (>80%)

### Mobile App Success Criteria 📋
- [ ] User can register and login
- [ ] User can create incidents with photos
- [ ] User can view their incident list
- [ ] Managers can view all incidents
- [ ] GPS location capture works
- [ ] File upload to backend works

### Integration Success Criteria 📋
- [ ] End-to-end incident creation flow works
- [ ] Authentication tokens work between app and API
- [ ] File upload and storage pipeline works
- [ ] Error handling and validation works across stack

## 🔍 Testing Strategy

### Backend Testing
```bash
# Run all tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Check test coverage
./gradlew jacocoTestReport
```

### Mobile Testing
```bash
# Run unit tests
npm test

# Run E2E tests (future)
npm run test:e2e
```

## 📋 Known Issues & Next Steps

### Current Issues
1. **Authentication Test Conflict**: User "realtest@safesnap.com" already exists
   - **Solution**: Use unique emails or clear test database

2. **Image Analysis Mock Mode**: Vision AI running in mock mode
   - **Solution**: Configure real Google Vision API credentials

### Phase 3 Planning
- [ ] Real AI integration (Google Vision, Whisper)
- [ ] RCA report management system
- [ ] Advanced mobile features (offline mode, push notifications)
- [ ] Manager dashboard with analytics
- [ ] Production deployment pipeline

## 🎯 Immediate Next Actions

1. **Fix Authentication Tests**
   ```bash
   # Use the simple auth test with unique emails
   ./simple-auth-test.sh
   ```

2. **Test Incident API**
   ```bash
   # Create a test incident via API
   curl -X POST http://localhost:8080/api/incidents \
     -H "Authorization: Bearer <token>"
     -d '<incident_data>'
   ```

3. **Start Mobile Development**
   ```bash
   # Initialize React Native project
   npx create-expo-app SafeSnapMobile --template blank-typescript
   ```

---

**Phase 2 Target Completion**: End of current sprint
**Key Milestone**: Working end-to-end incident creation from mobile app to backend
