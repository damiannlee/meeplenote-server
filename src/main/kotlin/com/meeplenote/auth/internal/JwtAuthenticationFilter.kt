package com.meeplenote.auth.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Bearer access 토큰을 파싱해 인증된 유저 ID를 SecurityContext의 principal로 설정한다. */
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val userId = jwtTokenProvider.parseUserIdOrNull(header.removePrefix("Bearer "))
            if (userId != null) {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(userId, null, emptyList())
            }
        }
        filterChain.doFilter(request, response)
    }
}
