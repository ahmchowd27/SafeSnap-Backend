/*
 * Copyright (c) 2025 SafeSnap Development Team
 * Licensed under the MIT License
 * See LICENSE file in the project root for full license information
 */
package com.safesnap.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SafeSnapBackendApplication

fun main(args: Array<String>) {
    runApplication<SafeSnapBackendApplication>(*args)
}
