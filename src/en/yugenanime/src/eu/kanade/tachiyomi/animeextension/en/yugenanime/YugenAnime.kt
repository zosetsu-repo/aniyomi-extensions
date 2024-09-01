package eu.kanade.tachiyomi.animeextension.en.yugenanime

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class YugenAnime : ParsedAnimeHttpSource() {

    override val name = "YugenAnime"

    override val baseUrl = "https://yugenanime.sx"

    override val lang = "en"

    override val supportsLatest = true

    override val client = OkHttpClient()

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request {
        val url = "$baseUrl/discover/?page=$page"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.cards-grid a.anime-meta"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.attr("title").ifBlank { element.select("span.anime-name").text() }
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.thumbnail_url = element.selectFirst("img.lozad")?.attr("data-src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "div.sidepanel--content > nav > ul > li:nth-child(7) > a"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/discover/?page=$page&sort=Newest+Addition"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = "div.cards-grid a.anime-meta"

    override fun latestUpdatesFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.attr("title").ifBlank { element.select("span.anime-name").text() }
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.thumbnail_url = element.selectFirst("img.lozad")?.attr("data-src")
        return anime
    }

    override fun latestUpdatesNextPageSelector(): String = "ul.pagination li.next a"

    // =============================== Search ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("NOTA: Los filtros no se aplican si se usa la búsqueda por texto"),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SeasonFilter(),
        YearFilter(),
        StudioFilter(),
    )

    private class GenreFilter : AnimeFilter.Select<String>(
        "Género",
        arrayOf(
            "Todos",
            "Acción",
            "Aventura",
            "Comedia",
            "Drama",
            "Fantasía",
        ),
    )

    private class StatusFilter : AnimeFilter.Select<String>(
        "Estado",
        arrayOf(
            "Todos",
            "Currently Airing",
            "Finished Airing",
            "Not yet aired",
        ),
    )

    private class TypeFilter : AnimeFilter.Select<String>(
        "Tipo",
        arrayOf(
            "Todos",
            "TV",
            "Movie",
            "OVA",
            "ONA",
            "Special",
        ),
    )

    private class SeasonFilter : AnimeFilter.Select<String>(
        "Temporada",
        arrayOf(
            "Todos",
            "Winter",
            "Spring",
            "Summer",
            "Fall",
        ),
    )

    private class YearFilter : AnimeFilter.Text("Año")

    private class StudioFilter : AnimeFilter.Text("Estudio")

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val urlBuilder = "$baseUrl/discover/".toHttpUrlOrNull()!!.newBuilder()

        urlBuilder.addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state != 0) {
                        urlBuilder.addQueryParameter("genre", filter.values[filter.state])
                    }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        urlBuilder.addQueryParameter("status", filter.values[filter.state].replace(" ", "+"))
                    }
                }
                is TypeFilter -> {
                    if (filter.state != 0) {
                        urlBuilder.addQueryParameter("type", filter.values[filter.state])
                    }
                }
                is SeasonFilter -> {
                    if (filter.state != 0) {
                        urlBuilder.addQueryParameter("season", filter.values[filter.state])
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank()) {
                        urlBuilder.addQueryParameter("year", filter.state)
                    }
                }
                is StudioFilter -> {
                    if (filter.state.isNotBlank()) {
                        urlBuilder.addQueryParameter("studio", filter.state)
                    }
                }
                else -> {}
            }
        }

        return GET(urlBuilder.toString(), headers)
    }
    override fun searchAnimeSelector(): String {
        throw NotImplementedError("This method should not be called!")
    }

    override fun videoFromElement(element: Element): Video {
        throw NotImplementedError("This method should not be called!")
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.attr("title").ifBlank { element.select("span.anime-name").text() }
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.thumbnail_url = (element.selectFirst("img.lozad")?.attr("data-src"))
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = "ul.pagination li.next a"

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.selectFirst("div.content h1")?.text().orEmpty()
        anime.thumbnail_url = document.selectFirst("img.cover")?.attr("src")

        val metaDetails = document.select("div.anime-metadetails div.data")
        metaDetails.forEach { data ->
            val title = data.selectFirst("div.ap--data-title")?.text()
            val description = data.selectFirst("span.description")?.text()

            when (title) {
                "Romaji" -> anime.title = description.orEmpty()
                "Studios" -> anime.author = description.orEmpty()
                "Status" -> anime.status = parseStatus(description.orEmpty())
                "Genres" -> anime.genre = description.orEmpty()
            }
        }

        anime.description = document.select("p.description").text()

        return anime
    }

    private fun parseStatus(status: String): Int {
        return when (status.lowercase()) {
            "finished airing" -> SAnime.COMPLETED
            "currently airing" -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "ul.ep-grid li.ep-card" // Selector de cada episodio en la lista
    override fun episodeListRequest(anime: SAnime): Request {
        val url = "$baseUrl${anime.url}watch/"
        Log.d("EpisodeListRequest", url)
        return GET(url, headers)
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val title = element.select("a.ep-title").text()
        val link = fixUrl(element.select("a.ep-title").attr("href"))

        val episodeNumber = title.substringBefore(":").filter { it.isDigit() }.toIntOrNull()

        episode.setUrlWithoutDomain(link)
        episode.name = title
        episode.episode_number = episodeNumber?.toFloat() ?: 0F

        return episode
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { element ->
            episodeFromElement(element)
        }
    }

    private fun fixUrl(url: String?): String {
        return when {
            url == null -> ""
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    // ============================ Video Links =============================
    override fun videoListParse(response: Response): List<Video> {
        val data = response.request.url.toString()
        val episode = data.removeSuffix("/").split("/").last()
        val dubData = data.substringBeforeLast("/$episode").let { "$it-dub/$episode" }

        val videoList = mutableListOf<Video>()

        listOf(data, dubData).forEach { url ->
            val doc = client.newCall(GET(url)).execute().asJsoup()
            val iframe = doc.select("iframe#main-embed").attr("src") ?: return@forEach
            val id = iframe.removeSuffix("/").split("/").lastOrNull() ?: return@forEach

            val sourceResponse = client.newCall(
                POST(
                    "$baseUrl/api/embed/",
                    body = FormBody.Builder()
                        .add("id", id)
                        .add("ac", "0")
                        .build(),
                    headers = headers.newBuilder()
                        .add("Referer", iframe)
                        .add("X-Requested-With", "XMLHttpRequest")
                        .build(),
                ),
            ).execute().body?.string()

            val source = sourceResponse?.parseAs<Sources>()?.hls?.distinct()?.firstOrNull() ?: return@forEach
            val isDub = if (url.contains("-dub")) "dub" else "sub"
            val sourceType = getSourceType(getBaseUrl(source))

            videoList.add(
                Video(
                    source,
                    "$sourceType [$isDub]",
                    source,
                    headers = headers,
                ),
            )
        }

        return videoList
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private fun getSourceType(url: String): String {
        return when {
            url.contains("cache", true) -> "Cache"
            url.contains("allanime", true) -> "Crunchyroll-AL"
            else -> Regex("\\.(\\S+)\\.").find(url)?.groupValues?.getOrNull(1)?.let { fixTitle(it) } ?: this.name
        }
    }

    private fun fixTitle(title: String): String {
        return title.replace("_", " ").capitalize()
    }

    override fun videoListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun videoUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    @Serializable
    data class Sources(
        @SerialName("hls")
        val hls: List<String>? = null,
    )
    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
