package com.safesnap.backend

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth.feature"],
    glue = ["com.safesnap.backend.steps"],
    plugin = ["pretty", "html:target/cucumber-reports"]
)
class CucumberIntegrationTest
