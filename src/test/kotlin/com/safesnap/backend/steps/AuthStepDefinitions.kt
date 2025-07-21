package com.safesnap.backend.steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.safesnap.backend.dto.user.AuthResponseDTO
import com.safesnap.backend.dto.user.LoginRequestDTO
import com.safesnap.backend.dto.user.UserCreateDTO
import com.safesnap.backend.entity.Role
import com.safesnap.backend.entity.User
import com.safesnap.backend.jwt.JwtService
import com.safesnap.backend.repository.UserRepository
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthStepDefinitions {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var lastResponse: ResponseEntity<String>? = null
    private var lastAuthResponse: AuthResponseDTO? = null
    private var currentUserToken: String? = null

    private fun getBaseUrl(): String = "http://localhost:$port"

    @Given("the SafeSnap API is running")
    fun theSafeSnapApiIsRunning() {
        // Just check that the test context is loaded - the app should be running
        assertTrue(port > 0, "Server port should be assigned")
    }

    @Given("the database is clean")
    fun theDatabaseIsClean() {
        userRepository.deleteAll()
        currentUserToken = null
        lastResponse = null
        lastAuthResponse = null
    }

    @When("I register with valid user details:")
    fun iRegisterWithValidUserDetails(dataTable: DataTable) {
        val userData = dataTable.asMaps(String::class.java, String::class.java)[0]
        val userCreateDTO = UserCreateDTO(
            name = userData["name"]!!,
            email = userData["email"]!!,
            password = userData["password"]!!,
            role = Role.valueOf(userData["role"]!!)
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val request = HttpEntity(userCreateDTO, headers)
        lastResponse = restTemplate.postForEntity("${getBaseUrl()}/api/auth/register", request, String::class.java)

        if (lastResponse?.statusCode == HttpStatus.OK) {
            lastAuthResponse = objectMapper.readValue(lastResponse?.body, AuthResponseDTO::class.java)
            currentUserToken = lastAuthResponse?.token
        }
    }

    @When("I register with valid manager details:")
    fun iRegisterWithValidManagerDetails(dataTable: DataTable) {
        iRegisterWithValidUserDetails(dataTable)
    }

    @Given("a user exists with email {string}")
    fun aUserExistsWithEmail(email: String) {
        val user = User(
            fullName = "Existing User",
            email = email,
            password = passwordEncoder.encode("password123"),
            role = Role.WORKER
        )
        userRepository.save(user)
    }

    @When("I register with email {string}")
    fun iRegisterWithEmail(email: String) {
        val userCreateDTO = UserCreateDTO(
            name = "Test User",
            email = email,
            password = "password123",
            role = Role.WORKER
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val request = HttpEntity(userCreateDTO, headers)
        lastResponse = restTemplate.postForEntity("${getBaseUrl()}/api/auth/register", request, String::class.java)
    }

    @When("I register with invalid email {string}")
    fun iRegisterWithInvalidEmail(email: String) {
        iRegisterWithEmail(email)
    }

    @Given("a user is registered with:")
    fun aUserIsRegisteredWith(dataTable: DataTable) {
        val userData = dataTable.asMaps(String::class.java, String::class.java)[0]
        val user = User(
            fullName = "Test User",
            email = userData["email"]!!,
            password = passwordEncoder.encode(userData["password"]!!),
            role = Role.valueOf(userData["role"]!!)
        )
        userRepository.save(user)
    }

    @Given("a user is registered with email {string}")
    fun aUserIsRegisteredWithEmail(email: String) {
        val user = User(
            fullName = "Test User",
            email = email,
            password = passwordEncoder.encode("password123"),
            role = Role.WORKER
        )
        userRepository.save(user)
    }

    @When("I login with email {string} and password {string}")
    fun iLoginWithEmailAndPassword(email: String, password: String) {
        val loginRequest = LoginRequestDTO(email = email, password = password)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val request = HttpEntity(loginRequest, headers)
        lastResponse = restTemplate.postForEntity("${getBaseUrl()}/api/auth/login", request, String::class.java)

        if (lastResponse?.statusCode == HttpStatus.OK) {
            lastAuthResponse = objectMapper.readValue(lastResponse?.body, AuthResponseDTO::class.java)
            currentUserToken = lastAuthResponse?.token
        }
    }

    @When("I access a protected endpoint without a token")
    fun iAccessAProtectedEndpointWithoutAToken() {
        lastResponse = restTemplate.getForEntity("${getBaseUrl()}/api/test/s3-health", String::class.java)
    }

    @Given("I am logged in as a user")
    fun iAmLoggedInAsAUser() {
        val user = User(
            fullName = "Logged In User",
            email = "loggedin@example.com",
            password = passwordEncoder.encode("password123"),
            role = Role.WORKER
        )
        val savedUser = userRepository.save(user)
        currentUserToken = jwtService.generateToken(savedUser.email, savedUser.role)
    }

    @When("I access a protected endpoint with my token")
    fun iAccessAProtectedEndpointWithMyToken() {
        val headers = HttpHeaders()
        headers.setBearerAuth(currentUserToken!!)

        val request = HttpEntity<String>(headers)
        lastResponse = restTemplate.exchange(
            "${getBaseUrl()}/api/test/s3-health",
            HttpMethod.GET,
            request,
            String::class.java
        )
    }

    @When("I access a protected endpoint with an invalid token")
    fun iAccessAProtectedEndpointWithAnInvalidToken() {
        val headers = HttpHeaders()
        headers.setBearerAuth("invalid.jwt.token")

        val request = HttpEntity<String>(headers)
        lastResponse = restTemplate.exchange(
            "${getBaseUrl()}/api/test/s3-health",
            HttpMethod.GET,
            request,
            String::class.java
        )
    }

    @Given("I am registered as a {string} with email {string}")
    fun iAmRegisteredAsAWithEmail(role: String, email: String) {
        val user = User(
            fullName = "Test $role",
            email = email,
            password = passwordEncoder.encode("password123"),
            role = Role.valueOf(role)
        )
        val savedUser = userRepository.save(user)
        currentUserToken = jwtService.generateToken(savedUser.email, savedUser.role)
        lastAuthResponse = AuthResponseDTO(currentUserToken!!, savedUser.role.name)
    }

    @When("I extract information from my JWT token")
    fun iExtractInformationFromMyJwtToken() {
        assertNotNull(currentUserToken, "No current token available")
    }

    @Then("I should receive a valid JWT token")
    fun iShouldReceiveAValidJwtToken() {
        assertEquals(HttpStatus.OK, lastResponse?.statusCode)
        assertNotNull(lastAuthResponse)
        assertNotNull(lastAuthResponse?.token)
        assertTrue(lastAuthResponse?.token?.isNotBlank() == true)

        val token = lastAuthResponse?.token!!
        val email = jwtService.extractUsername(token)
        assertTrue(jwtService.isTokenValid(token, email))
    }

    @And("the response should contain role {string}")
    fun theResponseShouldContainRole(expectedRole: String) {
        assertNotNull(lastAuthResponse)
        assertEquals(expectedRole, lastAuthResponse?.role)
    }

    @And("the user should be saved in the database")
    fun theUserShouldBeSavedInTheDatabase() {
        assertNotNull(lastAuthResponse)
        
        val token = lastAuthResponse?.token!!
        val email = jwtService.extractUsername(token)
        
        val user = userRepository.findByEmail(email)
        assertNotNull(user)
        assertEquals(email, user?.email)
    }

    @Then("I should receive an error response")
    fun iShouldReceiveAnErrorResponse() {
        assertTrue(lastResponse?.statusCode?.is4xxClientError == true)
    }

    @And("the error message should contain {string}")
    fun theErrorMessageShouldContain(expectedMessage: String) {
        assertNotNull(lastResponse?.body)
        assertTrue(lastResponse?.body?.contains(expectedMessage) == true)
    }

    @Then("I should receive a validation error")
    fun iShouldReceiveAValidationError() {
        assertEquals(HttpStatus.BAD_REQUEST, lastResponse?.statusCode)
    }

    @And("the error should mention email format")
    fun theErrorShouldMentionEmailFormat() {
        assertNotNull(lastResponse?.body)
        val responseBody = lastResponse?.body!!
        assertTrue(responseBody.contains("email", ignoreCase = true) || 
                  responseBody.contains("format", ignoreCase = true) ||
                  responseBody.contains("valid", ignoreCase = true))
    }

    @Then("I should receive an authentication error")
    fun iShouldReceiveAnAuthenticationError() {
        assertEquals(HttpStatus.UNAUTHORIZED, lastResponse?.statusCode)
    }

    @Then("I should receive an unauthorized error")
    fun iShouldReceiveAnUnauthorizedError() {
        assertEquals(HttpStatus.UNAUTHORIZED, lastResponse?.statusCode)
    }

    @Then("I should receive a successful response")
    fun iShouldReceiveASuccessfulResponse() {
        assertEquals(HttpStatus.OK, lastResponse?.statusCode)
    }

    @Then("the token should contain email {string}")
    fun theTokenShouldContainEmail(expectedEmail: String) {
        assertNotNull(currentUserToken)
        val extractedEmail = jwtService.extractUsername(currentUserToken!!)
        assertEquals(expectedEmail, extractedEmail)
    }

    @And("the token should contain role {string}")
    fun theTokenShouldContainRole(expectedRole: String) {
        assertNotNull(currentUserToken)
        val extractedRole = jwtService.extractRole(currentUserToken!!)
        assertEquals(expectedRole, extractedRole)
    }
}
