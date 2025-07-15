package com.safesnap.backend

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features"],
    glue = ["com.safesnap.backend.steps"],
    plugin = ["pretty"]
)
class CucumberIntegrationTest
