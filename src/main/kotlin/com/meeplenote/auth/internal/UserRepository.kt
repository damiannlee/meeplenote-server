package com.meeplenote.auth.internal

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): UserEntity?
}
