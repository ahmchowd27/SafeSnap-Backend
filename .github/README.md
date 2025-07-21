# SafeSnap CI/CD Pipeline 🚀

## Overview

This directory contains GitHub Actions workflows for SafeSnap's CI/CD pipeline, providing automated testing, security scanning, and deployment capabilities.

## Workflows

### 🧪 **build.yml** - Main CI/CD Pipeline
**Triggers:** Push to main/develop, Pull Requests
**Features:**
- ✅ **Multi-job pipeline** with parallel execution
- ✅ **Comprehensive testing** (Unit, Integration, BDD/Cucumber)
- ✅ **Test coverage** with JaCoCo reports  
- ✅ **Security scanning** with OWASP dependency check
- ✅ **Docker image building** and publishing
- ✅ **API documentation** generation
- ✅ **Performance testing** with load tests
- ✅ **Staging deployment** (main branch only)
- ✅ **Notifications** with Slack integration

**Jobs:**
1. **Test & Build** - Core application testing and building
2. **Security Scan** - OWASP dependency check and vulnerability scanning
3. **Docker** - Container image building and publishing  
4. **API Docs** - OpenAPI/Swagger documentation generation
5. **Performance** - Load testing and performance validation
6. **Deploy Staging** - Automated staging deployment
7. **Notify** - Status notifications and alerts

### 🚀 **deploy-production.yml** - Production Deployment
**Triggers:** Release published, Manual workflow dispatch
**Features:**
- ✅ **Production-ready builds** with optimized configurations
- ✅ **Zero-downtime deployment** strategies
- ✅ **Health checks** and smoke tests
- ✅ **Rollback capabilities** on failure
- ✅ **Monitoring setup** and alerts configuration
- ✅ **Stakeholder notifications**

**Process:**
1. Build production-optimized Docker image
2. Deploy to production infrastructure
3. Run comprehensive health checks
4. Configure monitoring and alerts
5. Notify stakeholders of successful deployment

### 🔒 **security-scan.yml** - Security & Compliance
**Triggers:** Daily schedule (2 AM UTC), Manual dispatch, build.gradle.kts changes
**Features:**
- ✅ **Daily security scans** for continuous monitoring
- ✅ **Dependency vulnerability** checking
- ✅ **Container security** scanning with Trivy
- ✅ **API security testing** (rate limiting, authentication)
- ✅ **Security reports** with actionable insights

## Required Secrets

Configure these secrets in your GitHub repository settings:

### Docker Hub
```
DOCKER_USERNAME=your-dockerhub-username
DOCKER_PASSWORD=your-dockerhub-token
```

### Production Environment
```
PRODUCTION_URL=https://api.safesnap.com
STAGING_URL=https://staging.safesnap.com
STAGING_DB_HOST=staging-db.example.com
```

### Optional Integrations
```
SLACK_WEBHOOK=https://hooks.slack.com/services/...
SONAR_TOKEN=your-sonarcloud-token
```

## Environment Variables

The workflows use these environment variables:

### Application Configuration
```yaml
SPRING_PROFILES_ACTIVE: test
GOOGLE_VISION_MOCK_MODE: true
RATE_LIMITING_ENABLED: false  # Disabled for tests
```

### Build Configuration
```yaml
JAVA_VERSION: '21'
GRADLE_OPTS: -Dorg.gradle.daemon=false
```

## Workflow Features

### 🎯 **Smart Triggering**
- **Feature branches**: Run tests only
- **Main/develop**: Full pipeline with Docker builds
- **Release tags**: Production deployment
- **Schedule**: Daily security scans

### 🔄 **Parallel Execution**
Jobs run in parallel where possible to minimize CI time:
- Tests and security scans run simultaneously
- Docker builds only after successful tests
- Documentation generation runs independently

### 🛡️ **Security First**
- Dependency vulnerability scanning
- Container image security checks
- API security testing
- Rate limiting validation
- Authentication verification

### 📊 **Comprehensive Reporting**
- Test coverage reports (JaCoCo)
- Security scan results
- Performance metrics
- API documentation
- Deployment status

### 🚨 **Failure Handling**
- Automatic rollback on production failures
- Detailed error notifications
- Artifact preservation for debugging
- Slack/email alerts for critical issues

## Usage Examples

### Manual Production Deployment
```bash
# Trigger production deployment manually
gh workflow run deploy-production.yml -f version=v1.2.0
```

### Security Scan
```bash
# Run security scan manually
gh workflow run security-scan.yml
```

### View Workflow Status
```bash
# Check latest workflow runs
gh run list --workflow=build.yml
```

## Local Testing

Before pushing, you can test some workflow steps locally:

### Build and Test
```bash
./gradlew clean build
./gradlew test
./gradlew jacocoTestReport
```

### Docker Build
```bash
docker build -t safesnap-backend:local .
docker run --rm safesnap-backend:local
```

### Security Check
```bash
# Test rate limiting locally
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test","password":"wrong"}'
done
```

## Monitoring

### GitHub Actions Insights
- Workflow success/failure rates
- Average execution times
- Resource usage patterns
- Security scan trends

### Key Metrics to Monitor
- **Build time**: Target < 5 minutes
- **Test coverage**: Maintain > 80%
- **Security vulnerabilities**: 0 critical, < 5 high
- **Deployment success rate**: > 95%

## Troubleshooting

### Common Issues

**Tests failing with 429 (Too Many Requests)**
- Rate limiting may be enabled in tests
- Check `application-test.properties` has `rate-limiting.enabled=false`

**Docker build failures**
- Verify Gradle build succeeds locally
- Check Docker Hub credentials in secrets
- Ensure adequate disk space in runner

**Security scan false positives**
- Review dependency-check suppression file
- Update vulnerable dependencies
- Consider alternative libraries if needed

**Deployment failures**
- Check production environment health
- Verify all required secrets are set
- Review deployment logs for specific errors

### Getting Help

1. **Check workflow logs** in GitHub Actions tab
2. **Review artifacts** uploaded by failed jobs  
3. **Test locally** using the same commands
4. **Check secrets** are properly configured
5. **Verify environment** variables match expectations

## Best Practices

### Commit Messages
Use conventional commits for better changelog generation:
```
feat: add rate limiting to authentication endpoints
fix: resolve JWT token validation issue
docs: update API documentation
```

### Branch Strategy
- `main`: Production-ready code, triggers full pipeline
- `develop`: Integration branch, triggers tests + Docker builds
- `feature/*`: Feature branches, triggers tests only
- `hotfix/*`: Emergency fixes, fast-tracked to production

### Security
- Never commit secrets or API keys
- Use environment variables for configuration
- Regularly update dependencies
- Monitor security advisories

---

**🎉 Your SafeSnap CI/CD pipeline is production-ready!**

*For questions or improvements, please create an issue or submit a pull request.*
