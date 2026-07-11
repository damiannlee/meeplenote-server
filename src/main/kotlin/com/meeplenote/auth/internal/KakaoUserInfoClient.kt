package com.meeplenote.auth.internal

import com.fasterxml.jackson.annotation.JsonProperty
import com.meeplenote.common.api.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

data class KakaoUserInfoResponse(
    val id: Long,
    @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount? = null,
    val properties: Properties? = null,
) {
    data class KakaoAccount(val profile: Profile? = null)
    data class Profile(val nickname: String? = null)
    data class Properties(val nickname: String? = null)

    fun nickname(): String =
        kakaoAccount?.profile?.nickname ?: properties?.nickname ?: "user$id"
}

@Component
class KakaoUserInfoClient(
    @Value("\${kakao.base-uri}") kakaoBaseUri: String,
) {
    private val restClient: RestClient = RestClient.builder().baseUrl(kakaoBaseUri).build()

    fun fetchUserInfo(kakaoAccessToken: String): KakaoUserInfoResponse =
        try {
            restClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer $kakaoAccessToken")
                .retrieve()
                .body(KakaoUserInfoResponse::class.java)
                ?: throw invalidTokenException()
        } catch (ex: RestClientException) {
            throw invalidTokenException()
        }

    private fun invalidTokenException() =
        BusinessException("INVALID_KAKAO_TOKEN", "카카오 인증에 실패했습니다", HttpStatus.UNAUTHORIZED)
}
