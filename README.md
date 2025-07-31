# SafeSnap 🏗️ - Safety Incident Reporting System

> **Current Status: Phase 3-4 Complete** - Backend API with JWT auth, incident management, AWS S3 integration, and Google Vision AI analysis

A full-stack safety incident reporting system designed for construction and warehouse crews. Workers can quickly report incidents with photos and location data while managers get comprehensive analysis tools.

## 🎯 What's Built So Far

### ✅ **Phase 1-3: Complete Backend Infrastructure**

#### 🔐 **Authentication & Security**
- **JWT-based authentication** with role-based access control (WORKER, MANAGER)
- **Spring Security** integration with method-level security
- **BCrypt password encryption**
- **Comprehensive error handling** with standardized API responses

#### 📊 **Database & Entities**
- **PostgreSQL** with JPA/Hibernate entities
- **User management** with roles (WORKER, MANAGER)
- **Incident reporting** with status tracking and assignments
- **Image analysis** and voice transcription entities
- **RCA (Root Cause Analysis)** reporting system
- **AI suggestions** storage for safety recommendations

#### 🛠️ **Core APIs Implemented**
- **Authentication**: Register, login with JWT tokens
- **Incident Management**: CRUD operations with filtering and pagination
- **S3 Integration**: Pre-signed URL generation for secure file uploads
- **Image Analysis**: Google Vision API integration with safety-focused tagging
- **Manager Tools**: Assignment, status updates, team oversight

#### 🧠 **AI & Analytics**
- **Google Vision API** integration for automatic safety hazard detection
- **Smart safety tagging** (PPE detection, hazard identification, workplace analysis)
- **Mock mode** for development without API costs
- **Comprehensive metrics** and monitoring

#### 🔧 **DevOps & Infrastructure**
- **Docker Compose** setup with PostgreSQL and LocalStack (mock AWS)
- **GitHub Actions CI/CD** with automated testing
- **JaCoCo code coverage** (80% minimum)
- **Gradle build** with Kotlin and Spring Boot
- **Comprehensive testing** with JUnit 5, Mockito, and Cucumber BDD

---

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Mobile App    │────│   Spring Boot    │────│   Amazon S3     │
│ (React Native)  │    │   Backend API    │    │  File Storage   │
│   [PLANNED]     │    │   ✅ COMPLETE    │    │  ✅ INTEGRATED  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                │                ┌───────┴──────┐
                                │                │ Google Vision │
                                │                │ API Analysis  │
                                │                │ ✅ INTEGRATED │
                                │                └──────────────┘
                       ┌────────┴────────┐
                       │   PostgreSQL    │
                       │   Database      │
                       │   ✅ COMPLETE   │
                       └─────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- **Java 21+**
- **Docker & Docker Compose**
- **Google Cloud Account** (optional - has mock mode)
- **PostgreSQL** (or use Docker)

### 1. Clone & Setup
```bash
git clone <your-repo-url>
cd safesnap
```

### 2. Environment Configuration
```bash
# Copy environment template
cp .env.example .env

# Edit with your actual values (optional - works with defaults)
nano .env
```

### 3. Start with Docker (Recommended)
```bash
# Start all services (PostgreSQL + LocalStack + App)
docker-compose up -d

# Check if everything is running
docker-compose ps
```

### 4. Alternative: Local Development
```bash
# Start just database
docker-compose up -d db

# Run application locally
./gradlew bootRun
```

### 5. Test the API
```bash
# Register a new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Worker",
    "email": "john@company.com", 
    "password": "SecurePass123!",
    "role": "WORKER"
  }'

# The response will include a JWT token for API access
```

---

## 📖 API Documentation

### **Live Documentation**
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

### **Key Endpoints**

#### 🔐 Authentication
```http
POST /api/auth/register    # Register new user
POST /api/auth/login       # User login
```

#### 📝 Incident Management
```http
GET    /api/incidents           # List user's incidents (paginated)
POST   /api/incidents           # Create new incident
GET    /api/incidents/{id}      # Get incident details
PUT    /api/incidents/{id}      # Update incident
DELETE /api/incidents/{id}      # Delete incident (MANAGER only)

# Manager-only endpoints
GET    /api/incidents/all                # All team incidents
PATCH  /api/incidents/{id}/status        # Update status
PATCH  /api/incidents/{id}/assign        # Assign to team member
```

#### 📁 File Management
```http
GET /api/s3/presigned-url/upload    # Get upload URL for images/audio
GET /api/s3/presigned-url/download  # Get download URL for files
```

#### 🤖 AI Analysis
```http
POST /api/image-analysis/analyze    # Analyze image for safety hazards
GET  /api/image-analysis/status     # Check Google Vision API status
```

---

## 🔧 Tech Stack

### **Backend (Current)**
- **Language**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.5.3
- **Security**: Spring Security + JWT
- **Database**: PostgreSQL 15 + JPA/Hibernate
- **Cloud**: AWS S3 + Google Cloud Vision API
- **Testing**: JUnit 5 + Mockito + Cucumber BDD
- **Build**: Gradle with Kotlin DSL
- **Monitoring**: Micrometer + Prometheus
- **Documentation**: OpenAPI 3 + Swagger UI

### **Infrastructure**
- **Containerization**: Docker + Docker Compose
- **CI/CD**: GitHub Actions
- **Development**: LocalStack (mock AWS services)
- **Code Quality**: JaCoCo coverage + detekt

### **Frontend (Planned)**
- **Framework**: React Native + Expo
- **Navigation**: React Navigation
- **State**: Redux Toolkit
- **Camera**: Expo Camera
- **Location**: Expo Location

---

## 🧪 Testing

### **Run Tests**
```bash
# All tests
./gradlew test

# With coverage report
./gradlew jacocoTestReport

# View coverage
open build/reports/jacoco/test/html/index.html
```

### **Test Coverage**
- **Current**: ~80% line coverage
- **Target**: 80% minimum (enforced)
- **Includes**: Unit tests, integration tests, BDD scenarios

### **Test Types**
- ✅ **Unit Tests**: Service layer, utilities, DTOs
- ✅ **Integration Tests**: Controller endpoints, database operations  
- ✅ **BDD Tests**: Cucumber scenarios for user workflows
- ✅ **Mock Tests**: External API integrations

---

## 🔒 Security Features

### **Authentication & Authorization**
- ✅ JWT tokens with configurable expiration
- ✅ Role-based access control (WORKER/MANAGER)
- ✅ Method-level security annotations
- ✅ Password strength validation
- ✅ BCrypt encryption

### **API Security**
- ✅ Rate limiting with Bucket4j
- ✅ CORS configuration
- ✅ Request validation
- ✅ Error message sanitization
- ✅ Secure file upload via pre-signed URLs

### **Data Protection**
- ✅ No sensitive data in logs
- ✅ Environment variable configuration
- ✅ Database password encryption
- ✅ Secure S3 object URLs

---

## 🚀 Deployment

### **Production Setup**
```bash
# Build production JAR
./gradlew bootJar

# Build Docker image
docker build -t safesnap:latest .

# Deploy with production compose
docker-compose -f docker-compose.prod.yml up -d
```

### **Environment Variables**
```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/safesnap
DB_USERNAME=safesnap_user
DB_PASSWORD=secure_password

# AWS S3
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_S3_BUCKET_NAME=safesnap-files
AWS_REGION=us-west-2

# Google Cloud (optional)
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
GOOGLE_VISION_PROJECT_ID=your_project_id

# Security
JWT_SECRET=your_jwt_secret_key_32chars_minimum
```

### **Health Checks**
- **Application**: http://localhost:8080/actuator/health
- **Database**: Included in health endpoint
- **S3**: Connection verified on startup
- **Google Vision**: Status endpoint available

---

## 📋 Development Roadmap

### ✅ **Phase 1-3: COMPLETE**
- [x] Core entities and DTOs
- [x] JWT authentication & Spring Security
- [x] S3 pre-signed URL support
- [x] Google Vision AI image analysis
- [x] Incident CRUD operations
- [x] Manager dashboard APIs

### 🔄 **Phase 4: IN PROGRESS** 
- [x] RCA (Root Cause Analysis) system
- [x] Advanced incident filtering
- [x] Metrics and monitoring
- [ ] Voice transcription (Whisper API)
- [ ] Performance optimization

### ⏳ **Phase 5: PLANNED**
- [ ] React Native mobile app
- [ ] Push notifications
- [ ] Offline support
- [ ] Advanced analytics dashboard

### 🚀 **Phase 6: FUTURE**
- [ ] Multi-tenant support
- [ ] Advanced AI suggestions
- [ ] Compliance reporting
- [ ] Integration APIs

---

## 🤝 Contributing

### **Getting Started**
1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Make changes with tests
4. Ensure 80%+ test coverage
5. Submit pull request

### **Development Standards**
- **Code Style**: Kotlin coding conventions
- **Testing**: All new features require tests
- **Documentation**: Update API docs and README
- **Security**: Follow OWASP guidelines
- **Performance**: Consider scalability

### **Commit Convention**
```
feat: add Google Vision AI integration
fix: resolve JWT token expiration issue
docs: update API documentation
test: add incident controller tests
```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

### **Commercial Use**
✅ Construction companies can use SafeSnap commercially  
✅ Full freedom to modify and adapt  
✅ Can be redistributed and sold  
✅ No patent restrictions  

---

## 🆘 Support & Contact

- **Issues**: [GitHub Issues](https://github.com/yourorg/safesnap/issues)
- **Documentation**: [Project Wiki](https://github.com/yourorg/safesnap/wiki)
- **API Docs**: http://localhost:8080/swagger-ui.html
- **Email**: safesnap-support@yourcompany.com

---

## 🏗️ What's Next?

SafeSnap backend is **production-ready** with comprehensive APIs, security, and AI integration. The next major milestone is the **React Native mobile app** to complete the full-stack solution.

**Key metrics achieved:**
- ✅ 30+ API endpoints implemented
- ✅ 80%+ test coverage maintained  
- ✅ Full CI/CD pipeline operational
- ✅ Production-grade security implemented
- ✅ AI-powered safety analysis working
- ✅ Docker deployment ready

---

*Built with ❤️ for safer workplaces | SafeSnap Development Team © 2025*
