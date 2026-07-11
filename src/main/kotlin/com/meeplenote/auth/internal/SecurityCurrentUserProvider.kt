package com.meeplenote.auth.internal

import com.meeplenote.auth.api.CurrentUserProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/** Reads the user id that JwtAuthenticationFilter placed as the Authentication principal. */
@Component
class SecurityCurrentUserProvider : CurrentUserProvider {
    override fun currentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authenticated user in the security context")
        return authentication.principal as Long
    }
}
