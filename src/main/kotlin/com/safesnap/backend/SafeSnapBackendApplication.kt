package com.safesnap.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SafeSnapBackendApplication

fun main(args: Array<String>) {
    runApplication<SafeSnapBackendApplication>(*args)
}
