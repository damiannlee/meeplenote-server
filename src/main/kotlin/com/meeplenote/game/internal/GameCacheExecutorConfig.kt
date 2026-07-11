package com.meeplenote.game.internal

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class GameCacheExecutorConfig {

    /** Dedicated thread pool for BGG cache-miss handling, kept separate so request threads never block waiting on BGG. */
    @Bean
    fun gameCacheExecutor(): Executor = Executors.newFixedThreadPool(4)
}
