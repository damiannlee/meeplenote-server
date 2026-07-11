package com.meeplenote.game.internal

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BggClientTest {

    private lateinit var server: MockWebServer
    private lateinit var bggClient: BggClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        bggClient = BggClient(server.url("/").toString().removeSuffix("/"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search는 primary name과 id를 파싱한다`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <items total="1">
                        <item type="boardgame" id="13">
                            <name type="primary" value="CATAN"/>
                            <yearpublished value="1995" />
                        </item>
                    </items>
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "text/xml"),
        )

        val results = bggClient.search("catan")

        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo(13L)
        assertThat(results[0].name).isEqualTo("CATAN")
    }

    @Test
    fun `fetchThings는 썸네일과 인원수를 파싱한다`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <items>
                        <item type="boardgame" id="13">
                            <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
                            <name type="primary" sortindex="1" value="CATAN" />
                            <name type="alternate" sortindex="1" value="Los Colonos de Catan" />
                            <minplayers value="3" />
                            <maxplayers value="4" />
                            <playingtime value="120" />
                        </item>
                    </items>
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "text/xml"),
        )

        val results = bggClient.fetchThings(listOf(13L))

        assertThat(results).hasSize(1)
        val detail = results[0]
        assertThat(detail.id).isEqualTo(13L)
        assertThat(detail.name).isEqualTo("CATAN")
        assertThat(detail.thumbnailUrl).isEqualTo("https://cf.geekdo-images.com/thumb.jpg")
        assertThat(detail.minPlayers).isEqualTo(3)
        assertThat(detail.maxPlayers).isEqualTo(4)
        assertThat(detail.playtimeMinutes).isEqualTo(120)
    }

    @Test
    fun `빈 id 목록은 네트워크 호출 없이 빈 리스트를 반환한다`() {
        val results = bggClient.fetchThings(emptyList())

        assertThat(results).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }
}
