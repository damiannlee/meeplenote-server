package com.meeplenote.game.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.w3c.dom.Element
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

data class BggSearchResult(val id: Long, val name: String)

data class BggGameDetail(
    val id: Long,
    val name: String,
    val thumbnailUrl: String?,
    val minPlayers: Short?,
    val maxPlayers: Short?,
    val playtimeMinutes: Short?,
)

/** On-demand BGG XML API2 client (ADR-003) — search finds candidates, thing bulk-fetches their detail. */
@Component
class BggClient(
    @Value("\${bgg.base-uri}") bggBaseUri: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = RestClient.builder()
        .baseUrl(bggBaseUri)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(2_000)
                setReadTimeout(3_000)
            },
        )
        .build()

    // XXE hardening — BGG is trusted, but any externally received XML is parsed defensively regardless
    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

    /** Absorbs any BGG failure (4xx/5xx, timeout, parse error) into an empty result so the search loop never breaks (ADR-003). */
    fun search(query: String): List<BggSearchResult> = runCatching {
        // Pass as a URI template variable so RestClient encodes it exactly once
        // (manually URL-encoding first and then concatenating would make RestClient encode it again).
        val xml = restClient.get()
            .uri("/search?query={query}&type=boardgame", query)
            .retrieve()
            .body(String::class.java) ?: return emptyList()

        val items = parseXml(xml).getElementsByTagName("item")
        (0 until items.length).mapNotNull { i ->
            val item = items.item(i) as Element
            val id = item.getAttribute("id").toLongOrNull() ?: return@mapNotNull null
            val name = primaryName(item) ?: return@mapNotNull null
            BggSearchResult(id, name)
        }
    }.getOrElse {
        log.warn("BGG search failed, falling back to empty result: {}", it.message)
        emptyList()
    }

    fun fetchThings(ids: List<Long>): List<BggGameDetail> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            val xml = restClient.get()
                .uri("/thing?id=${ids.joinToString(",")}&type=boardgame")
                .retrieve()
                .body(String::class.java) ?: return emptyList()

            val items = parseXml(xml).getElementsByTagName("item")
            (0 until items.length).mapNotNull { i ->
                val item = items.item(i) as Element
                val id = item.getAttribute("id").toLongOrNull() ?: return@mapNotNull null
                val name = primaryName(item) ?: return@mapNotNull null
                BggGameDetail(
                    id = id,
                    name = name,
                    thumbnailUrl = item.firstChildText("thumbnail"),
                    minPlayers = item.attrValue("minplayers")?.toShortOrNull(),
                    maxPlayers = item.attrValue("maxplayers")?.toShortOrNull(),
                    playtimeMinutes = item.attrValue("playingtime")?.toShortOrNull(),
                )
            }
        }.getOrElse {
            log.warn("BGG thing lookup failed, falling back to empty result: {}", it.message)
            emptyList()
        }
    }

    private fun parseXml(xml: String) =
        documentBuilderFactory.newDocumentBuilder().parse(xml.byteInputStream(StandardCharsets.UTF_8))

    private fun primaryName(item: Element): String? {
        val names = item.getElementsByTagName("name")
        for (i in 0 until names.length) {
            val nameEl = names.item(i) as Element
            if (nameEl.getAttribute("type") == "primary") {
                return nameEl.getAttribute("value")
            }
        }
        return (names.item(0) as? Element)?.getAttribute("value")
    }

    private fun Element.firstChildText(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) (nodes.item(0) as Element).textContent?.takeIf { it.isNotBlank() } else null
    }

    private fun Element.attrValue(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) (nodes.item(0) as Element).getAttribute("value") else null
    }
}
