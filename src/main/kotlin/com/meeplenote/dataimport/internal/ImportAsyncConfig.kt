package com.meeplenote.dataimport.internal

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/** ADR-005: 임포트는 인프로세스 비동기 잡. MAU 1,000 규모라 스레드풀은 작게 유지 (ADR-002 과설계 금지). */
@Configuration
@EnableAsync
class ImportAsyncConfig {

    @Bean(name = ["importTaskExecutor"])
    fun importTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 2
        executor.setQueueCapacity(50)
        executor.setThreadNamePrefix("import-job-")
        executor.initialize()
        return executor
    }
}
