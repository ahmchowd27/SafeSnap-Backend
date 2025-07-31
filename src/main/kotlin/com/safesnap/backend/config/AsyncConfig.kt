package com.safesnap.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1  // Reduced to prevent concurrent issues
        executor.maxPoolSize = 2  // Reduced to prevent concurrent issues
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("ImageProcessing-")
        executor.initialize()
        return executor
    }
}
