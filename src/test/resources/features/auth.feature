Feature: User Authentication
  As a SafeSnap user
  I want to register and login to the system
  So that I can report and manage safety incidents

  Background:
    Given the SafeSnap API is running
    And the database is clean

  Scenario: User registers successfully
    When I register with valid user details:
      | name     | email           | password    | role |
      | John Doe | john@example.com| password123 | USER |
    Then I should receive a valid JWT token
    And the response should contain role "USER"
    And the user should be saved in the database

  Scenario: Manager registers successfully
    When I register with valid manager details:
      | name        | email              | password    | role    |
      | Jane Manager| manager@example.com| password123 | MANAGER |
    Then I should receive a valid JWT token
    And the response should contain role "MANAGER"

  Scenario: Registration fails with duplicate email
    Given a user exists with email "existing@example.com"
    When I register with email "existing@example.com"
    Then I should receive an error response
    And the error message should contain "Email already registered"

  Scenario: Registration fails with invalid email
    When I register with invalid email "not-an-email"
    Then I should receive a validation error
    And the error should mention email format

  Scenario: User logs in successfully
    Given a user is registered with:
      | email           | password    | role |
      | john@example.com| password123 | USER |
    When I login with email "john@example.com" and password "password123"
    Then I should receive a valid JWT token
    And the response should contain role "USER"

  Scenario: Login fails with wrong password
    Given a user is registered with email "john@example.com"
    When I login with email "john@example.com" and password "wrongpassword"
    Then I should receive an authentication error

  Scenario: Login fails with non-existent email
    When I login with email "nonexistent@example.com" and password "password123"
    Then I should receive an authentication error

  Scenario: Protected endpoint requires authentication
    When I access a protected endpoint without a token
    Then I should receive an unauthorized error

  Scenario: Protected endpoint accepts valid token
    Given I am logged in as a user
    When I access a protected endpoint with my token
    Then I should receive a successful response

  Scenario: Protected endpoint rejects invalid token
    When I access a protected endpoint with an invalid token
    Then I should receive an unauthorized error

  Scenario Outline: Token contains correct user information
    Given I am registered as a "<role>" with email "<email>"
    When I extract information from my JWT token
    Then the token should contain email "<email>"
    And the token should contain role "<role>"

    Examples:
      | email              | role    |
      | user@example.com   | USER    |
      | manager@example.com| MANAGER |
