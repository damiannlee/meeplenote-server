package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository

interface PlayPlayerRepository : JpaRepository<PlayPlayerEntity, Long>
