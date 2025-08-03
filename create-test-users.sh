#!/bin/bash

echo "👥 SafeSnap Test User Setup"
echo "=========================="

# Configuration
BACKEND_URL="http://localhost:8080"
WORKER_EMAIL="integration-worker@safesnap.test"
WORKER_PASSWORD="IntegrationTest123!"
MANAGER_EMAIL="integration-manager@safesnap.test"
MANAGER_PASSWORD="IntegrationTest123!"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if server is running
if ! curl -s "${BACKEND_URL}/actuator/health" > /dev/null; then
    log_error "SafeSnap server is not running on ${BACKEND_URL}"
    echo "Please start the server with: ./gradlew bootRun"
    exit 1
fi

log_info "Creating dedicated test user accounts..."

echo ""
echo "Creating Worker Account:"
echo "Email: $WORKER_EMAIL"
echo "Password: $WORKER_PASSWORD"

WORKER_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Integration Test Worker\",
    \"email\": \"$WORKER_EMAIL\",
    \"password\": \"$WORKER_PASSWORD\",
    \"role\": \"WORKER\"
  }")

if echo "$WORKER_RESPONSE" | grep -q "token"; then
    log_success "Worker account created successfully"
elif echo "$WORKER_RESPONSE" | grep -q -i "already"; then
    log_info "Worker account already exists"
else
    log_error "Failed to create worker account"
    echo "Response: $WORKER_RESPONSE"
fi

echo ""
echo "Creating Manager Account:"
echo "Email: $MANAGER_EMAIL"
echo "Password: $MANAGER_PASSWORD"

MANAGER_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Integration Test Manager\",
    \"email\": \"$MANAGER_EMAIL\",
    \"password\": \"$MANAGER_PASSWORD\",
    \"role\": \"MANAGER\"
  }")

if echo "$MANAGER_RESPONSE" | grep -q "token"; then
    log_success "Manager account created successfully"
elif echo "$MANAGER_RESPONSE" | grep -q -i "already"; then
    log_info "Manager account already exists"
else
    log_error "Failed to create manager account"
    echo "Response: $MANAGER_RESPONSE"
fi

echo ""
log_success "Test user setup complete!"
echo ""
echo "Test Credentials:"
echo "=================="
echo "Worker:  $WORKER_EMAIL / $WORKER_PASSWORD"
echo "Manager: $MANAGER_EMAIL / $MANAGER_PASSWORD"
echo ""
echo "These accounts are dedicated for integration testing and can be safely"
echo "cleaned up using: ./cleanup-test-data.sh"
