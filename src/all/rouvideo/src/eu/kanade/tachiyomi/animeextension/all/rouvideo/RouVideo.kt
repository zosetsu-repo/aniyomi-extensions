package eu.kanade.tachiyomi.animeextension.all.rouvideo

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class RouVideo : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "肉視頻"

    override val baseUrl = "https://rou.video"

    private val apiUrl = "https://rou.video/api"

    override val lang = "all"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("v")
            addQueryParameter("order", SORT_LIKE_KEY)
            addQueryParameter("page", page.toString())
        }.build(),
        apiHeaders,
    )

    override fun popularAnimeSelector() = "div.my-auto:has(a[href*=\"/$VIDEO_SLUG/\"])"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val url = element.selectFirst("a[href*=\"/$VIDEO_SLUG/\"]")!!.attr("href")
        val category = element.select("a[href*=\"/$CATEGORY_SLUG/\"]").attr("href")
            .removeSuffix("/")
            .substringAfterLast('/')
        val thumb = element.select("a > img").attr("abs:src")
        val code = element.select("a > img ~ div:nth-child(4)").text()
        return SAnime.create().apply {
            setUrlWithoutDomain(url)
            title = element.select("a > div.text-sm").text()
            artist = category
            author = category
            genre = listOf(category, code).joinToString()
            thumbnail_url = thumb
            status = SAnime.COMPLETED
            // update_strategy = AnimeUpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun popularAnimeNextPageSelector() = "nav[aria-label='Pagination'] > a:not([aria-current='page']) + a > span:contains(Next)"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("v")
            addQueryParameter("order", SORT_LATEST_KEY)
            addQueryParameter("page", page.toString())
        }.build(),
        apiHeaders,
    )

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as GenreFilter

        val url = when {
            genreFilter.state != 0 -> apiUrl + genreFilter.toUriPart()
            else -> "$apiUrl/?title=$query"
        }

        return GET(url, headers = apiHeaders)
    }

    override fun searchAnimeSelector(): String {
        TODO("Not yet implemented")
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        TODO("Not yet implemented")
    }

    override fun searchAnimeNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("NOTE: Filters are going to be ignored if using search text"),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<select>", ""),
            Pair("Comedy", "/?genre=\"Comedy\""),
            Pair("Drama", "/?genre=\"Drama\""),
            Pair("Action", "/?genre=\"Action\""),
            Pair("Fantasy", "/?genre=\"Fantasy\""),
            Pair("Supernatural", "/?genre=\"Supernatural\""),
            Pair("Latest Movie", "/?sort={\"startDate\": -1 }&type=MOVIE"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // =========================== Anime Details ============================

    override fun getAnimeUrl(anime: SAnime): String {
        return anime.url
    }

    override fun animeDetailsRequest(anime: SAnime): Request {
        val data = json.decodeFromString<LinkData>(anime.url)
        return GET("$baseUrl/anime/${data.slug}", headers = docHeaders)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val data = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return SAnime.create()

        return json.decodeFromString<AnimeDetails>(data).props.pageProps.data.toSAnime()
    }

    override fun animeDetailsParse(document: Document): SAnime {
        TODO("Not yet implemented")
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val data = json.decodeFromString<LinkData>(anime.url)
        return GET("$apiUrl/anime/${data.id}/episode", headers = apiHeaders)
    }

    override fun episodeListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val data = response.parseAs<EpisodeListResponse>()
        return data.data.map { it.toSEpisode() }.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode {
        TODO("Not yet implemented")
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers = docHeaders)
    override fun videoListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun videoUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    override fun videoFromElement(element: Element): Video {
        TODO("Not yet implemented")
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val data = json.decodeFromString<VideoData>(
            document.selectFirst("script#__NEXT_DATA__")!!.data(),
        ).props.pageProps

        val subtitleList = data.subtitles?.map {
            Track(it.src, it.label)
        } ?: emptyList()

        val videoListUrl = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("storage")
            addPathSegment(data.animeData.title)
            addPathSegment(data.episode.number)
        }.build().toString()

        val videoObjectList = client.newCall(
            GET(videoListUrl, headers = apiHeaders),
        ).execute().parseAs<VideoList>()

        if (videoObjectList.directUrl == null) {
            throw Exception(videoObjectList.message ?: "Videos not found")
        }

        val videoHeaders = headers.newBuilder().apply {
            add("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5")
            add("Host", apiUrl.toHttpUrl().host)
            add("Referer", "$baseUrl/")
        }.build()

        return videoObjectList.directUrl.map {
            val videoUrl = when {
                it.src.startsWith("//") -> "https:${it.src}"
                it.src.startsWith("/") -> apiUrl + it.src
                else -> it.src
            }

            Video(videoUrl, it.label, videoUrl, headers = videoHeaders, subtitleTracks = subtitleList)
        }.ifEmpty { throw Exception("Failed to fetch videos") }
    }

    // ============================= Utilities ==============================

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

        return this.sortedWith(
            compareBy(
                { it.quality.contains(quality) },
                { Regex("""(\d+)p""").find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "1080"

        private const val VIDEO_SLUG = "v"
        private const val CATEGORY_SLUG = "t"

        private const val SORT_LATEST_KEY = "createdAt"
        private const val SORT_LIKE_KEY = "likeCount"
        private const val SORT_VIEW_KEY = "viewCount"
    }
    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }
}
