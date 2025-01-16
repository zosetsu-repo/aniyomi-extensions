package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideoDto.toAnimePage
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideoFilters.ALL_VIDEOS
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideoFilters.FEATURED
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideoFilters.WATCHING
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class RouVideo : AnimeHttpSource() {

    override val name = "肉視頻"

    override val baseUrl = "https://rou.video"

    private val apiUrl = "https://rou.video/api"

    override val lang = "all"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val apiHeaders = headers.newBuilder().apply {
        add("Accept", "application/json, text/plain, */*")
        add("Host", apiUrl.toHttpUrl().host)
        add("Origin", baseUrl)
        add("Referer", "$baseUrl/")
    }.build()

    private val docHeaders = headers.newBuilder().apply {
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Host", baseUrl.toHttpUrl().host)
    }.build()

    private val videoHeaders by lazy {
        headers.newBuilder().apply {
            add("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5")
            add("Host", apiUrl.toHttpUrl().host)
            add("Referer", "$baseUrl/")
        }.build()
    }

    private val playlistUtils by lazy { PlaylistUtils(client) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("v")
            addQueryParameter("order", SORT_LIKE_KEY)
            addQueryParameter("page", page.toString())
        }.build(),
        docHeaders,
    )

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val data = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: return AnimesPage(emptyList(), false)

        return json.decodeFromString<RouVideoDto.VideoList>(data)
            .props.pageProps.toAnimePage()
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("v")
            addQueryParameter("order", SORT_LATEST_KEY)
            addQueryParameter("page", page.toString())
        }.build(),
        docHeaders,
    )

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val categoryFilter = filters.filterIsInstance<RouVideoFilters.CategoryFilter>().firstOrNull()
        val sortFilter = filters.filterIsInstance<RouVideoFilters.SortFilter>().firstOrNull()

        if (query.isBlank() && categoryFilter?.toUriPart() == WATCHING) {
            return GET(watchingURL, apiHeaders)
        }

        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                if (query.isBlank()) {
                    when {
                        categoryFilter == null || categoryFilter.toUriPart() == FEATURED ->
                            addPathSegment("home")
                        categoryFilter.toUriPart() == ALL_VIDEOS ->
                            addPathSegment(VIDEO_SLUG)
                        else ->
                            addPathSegments("$CATEGORY_SLUG/${categoryFilter.toUriPart()}")
                    }
                    sortFilter?.let { addQueryParameter("order", it.toUriPart()) }
                } else {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                    categoryFilter?.let { addQueryParameter(CATEGORY_SLUG, it.toUriPart()) }
                }
                addQueryParameter("page", page.toString())
            }.build(),
            docHeaders,
        )
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val request = response.request.url
        return when {
            request.toString().contains(watchingURL) -> {
                val jsonStr = response.body.string()
                json.decodeFromString<List<RouVideoDto.Video>>(jsonStr)
                    .toAnimePage()
            }
            request.toString().contains("$baseUrl/home") -> {
                val document = response.asJsoup()
                document.selectFirst("script#__NEXT_DATA__")?.data()
                    ?.let {
                        json.decodeFromString<RouVideoDto.HotVideoList>(it)
                            .props.pageProps.toAnimePage()
                    }
                    ?: AnimesPage(emptyList(), false)
            }
            else -> popularAnimeParse(response)
        }
    }

    private val hotListURL by lazy { "$apiUrl/home" }
    private val watchingURL by lazy { "$apiUrl/$VIDEO_SLUG/watching" }

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        RouVideoFilters.CategoryFilter(),
        AnimeFilter.Header("NOTE: Sort is ignored if using text search"),
        RouVideoFilters.SortFilter(),
    )

    // =========================== Anime Details ============================

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/$VIDEO_SLUG/${anime.url}"

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val resolutionRegex = "(Resolution: \\d+p)".toRegex()
        val resolution = anime.description?.let { resolutionRegex.find(it) }
            ?.groupValues?.first()
        return client.newCall(animeDetailsRequest(anime))
            .execute().asJsoup()
            .let { document ->
                val data = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return SAnime.create()
                json.decodeFromString<RouVideoDto.VideoDetails>(data).props.pageProps.video.toSAnime()
                    .apply {
                        // Search & RelatedVideos doesn't have likeCount while AnimeDetails doesn't have resolution
                        val resolutionSet = description?.matches(resolutionRegex) ?: false
                        if (!resolutionSet && !resolution.isNullOrBlank()) {
                            description = "$resolution\n$description"
                        }
                    }
            }
    }

    override fun animeDetailsRequest(anime: SAnime) = GET(getAnimeUrl(anime), docHeaders)

    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        return GET("$baseUrl/$VIDEO_SLUG/${anime.url}", docHeaders)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val data = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
        val video = json.decodeFromString<RouVideoDto.VideoDetails>(data).props.pageProps.video

        return listOf(video.toEpisode())
    }

    override fun getEpisodeUrl(episode: SEpisode) = "$baseUrl/$VIDEO_SLUG/${episode.url}"

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode) = GET("$apiUrl/$VIDEO_SLUG/${episode.url}", apiHeaders)

    override fun videoListParse(response: Response): List<Video> {
        val jsonStr = response.body.string()
        val data = json.decodeFromString<RouVideoDto.VideoData>(jsonStr).video

        return playlistUtils.extractFromHls(
            playlistUrl = data.videoUrl,
            referer = "$baseUrl/",
        )
    }

    // ============================= Utilities ==============================

    companion object {
        private const val VIDEO_SLUG = "v"
        private const val CATEGORY_SLUG = "t"

        const val SORT_LATEST_KEY = "createdAt"
        const val SORT_LIKE_KEY = "likeCount"
        const val SORT_VIEW_KEY = "viewCount"
    }
}
