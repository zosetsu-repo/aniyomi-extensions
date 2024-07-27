package eu.kanade.tachiyomi.animeextension.es.cinecalidad

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
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Cinecalidad : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Cinecalidad"

    override val baseUrl = "https://www.cinecalidad.ec"

    override val lang = "es"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/")

    override fun popularAnimeSelector(): String = "div.custom.animation-2.items.normal article"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.select("div.poster div.in_title").text()
        anime.thumbnail_url = element.select("div.poster img").attr("data-src")
        anime.setUrlWithoutDomain(element.select("div.poster > a").attr("href"))
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "div.wp-pagenavi a.nextpostslink"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = "$baseUrl/fecha-de-lanzamiento/2024/page/$page".let(::GET)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("La busqueda por texto ignora el filtro"),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Tipos",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Series", "ver-serie"),
            Pair("Acción", "genero-de-la-pelicula/accion"),
            Pair("Animación", "genero-de-la-pelicula/animacion"),
            Pair("Anime", "genero-de-la-pelicula/anime"),
            Pair("Aventura", "genero-de-la-pelicula/aventura"),
            Pair("Bélico", "genero-de-la-pelicula/belica"),
            Pair("Ciencia ficción", "genero-de-la-pelicula/ciencia-ficcion"),
            Pair("Crimen", "genero-de-la-pelicula/crimen"),
            Pair("Comedia", "genero-de-la-pelicula/comedia"),
            Pair("Documental", "genero-de-la-pelicula/documental"),
            Pair("Drama", "genero-de-la-pelicula/drama"),
            Pair("Familiar", "genero-de-la-pelicula/familia"),
            Pair("Fantasía", "genero-de-la-pelicula/fantasia"),
            Pair("Historia", "genero-de-la-pelicula/historia"),
            Pair("Música", "genero-de-la-pelicula/musica"),
            Pair("Misterio", "genero-de-la-pelicula/misterio"),
            Pair("Terror", "genero-de-la-pelicula/terror"),
            Pair("Suspenso", "genero-de-la-pelicula/suspense"),
            Pair("Romance", "genero-de-la-pelicula/romance"),
            Pair("Dc Comics", "genero-de-la-pelicula/peliculas-de-dc-comics-online-cinecalidad"),
            Pair("Marvel", "genero-de-la-pelicula/universo-marvel"),
        ),
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // =============================== Search ===============================
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/?s=$id", headers))
                .awaitSuccess()
                .use(::searchAnimeByIdParse)
        } else {
            super.getSearchAnime(page, query, filters)
        }
    }

    private fun searchAnimeByIdParse(response: Response): AnimesPage {
        val details = animeDetailsParse(response.asJsoup())
            .apply {
                setUrlWithoutDomain(response.request.url.toString())
                initialized = true
            }
        return AnimesPage(listOf(details), false)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as GenreFilter
        return when {
            query.isNotBlank() -> GET("$baseUrl/page/$page/?s=$query")
            genreFilter.state != 0 -> GET("$baseUrl/${genreFilter.toUriPart()}/page/$page/")
            else -> popularAnimeRequest(page)
        }
    }

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        anime.title = document.select("div.single_left h1").text()

        anime.thumbnail_url = document.select("div.single_left img").attr("data-src")

        val description: String

        val fbLikeDescription = document.select("div.single_left > table > tbody > tr > td:nth-child(2) > p:nth-child(4)").first()?.text()
        val tdDescription = document.select("td[style='text-align:justify;'] > p").first()?.text()

        description = if (fbLikeDescription != null && fbLikeDescription.contains("Títulos:")) {
            tdDescription ?: "sin descripción"
        } else {
            fbLikeDescription ?: tdDescription ?: "sin descripción"
        }

        anime.description = description

        val genres = document.select("span:contains(Género:) a").map { it.text() }

        if (genres.isNotEmpty()) {
            anime.genre = genres.joinToString(", ")
        }
        val creators = document.select("span:contains(Creador:) a").map { it.text() }

        if (creators.isNotEmpty()) {
            anime.author = creators.joinToString(", ")
        }

        val cast = document.select("span:contains(Elenco:) a").map { it.text() }

        if (cast.isNotEmpty()) {
            anime.artist = cast.joinToString(", ")
        }

        return anime
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector() = "uwu"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val isMovie = document.select(".single_left h1").isNotEmpty() && document.select("div.se-c div.se-a ul.episodios").isEmpty()

        return if (isMovie) {
            listOf(
                SEpisode.create().apply {
                    name = "Película"
                    setUrlWithoutDomain(response.request.url.toString())
                    episode_number = 1f
                },
            )
        } else {
            document.select("div.se-c div.se-a ul.episodios li").map { element ->
                val episodeLink = element.selectFirst("a")!!.attr("href")
                val seasonMatch = Regex("S(\\d+)-E(\\d+)").find(element.selectFirst(".numerando")!!.text())
                val seasonNumber = seasonMatch?.groups?.get(1)?.value?.toInt() ?: 1
                val episodeNumber = seasonMatch?.groups?.get(2)?.value?.toInt() ?: 0
                val episodeTitle = element.selectFirst(".episodiotitle a")!!.text()

                SEpisode.create().apply {
                    name = "T$seasonNumber - E$episodeNumber: $episodeTitle"
                    setUrlWithoutDomain(episodeLink)
                    episode_number = episodeNumber.toFloat()
                }
            }
        }.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode { throw UnsupportedOperationException() }

    // ============================ Video Links =============================

    /*--------------------------------Video extractors------------------------------------*/
    private val streamTapeExtractor by lazy { StreamTapeExtractor(client) }
    private val voeExtractor by lazy { VoeExtractor(client) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select("li.dooplay_player_option").flatMap { element ->
            val server = element.text()
            val url = element.attr("data-option")

            when {
                server.contains("streamtape") || server.contains("stp") || server.contains("stape") -> {
                    listOfNotNull(streamTapeExtractor.videoFromUrl(url, quality = "StreamTape"))
                }
                server.contains("okru") || server.contains("ok.") -> okruExtractor.videosFromUrl(url, fixQualities = true)
                server.contains("voe") -> voeExtractor.videosFromUrl(url)
                server.contains("filemoon") -> filemoonExtractor.videosFromUrl(url, prefix = "Filemoon:")
                server.contains("wishembed") || server.contains("streamwish") || server.contains("strwish") || server.contains("wish") -> {
                    streamWishExtractor.videosFromUrl(url, videoNameGen = { "StreamWish:$it" })
                }
                server.contains("doodstream") || server.contains("dood.") || server.contains("ds2play") || server.contains("doods.") -> {
                    doodExtractor.videosFromUrl(url, "DoodStream")
                }
                server.contains("vidhide") || server.contains("vid.") -> {
                    vidHideExtractor.videosFromUrl(url) { "VidHide:$it" }
                }
                else -> emptyList()
            }
        }
    }

    override fun videoListSelector(): String = "li.dooplay_player_option"

    override fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException()
    }

    override fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        val server = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT)!!
        val lang = preferences.getString(PREF_LANGUAGE_KEY, PREF_LANGUAGE_DEFAULT)!!
        return this.sortedWith(
            compareBy(
                { it.quality.contains(lang, true) },
                { it.quality.contains(server, true) },
                { it.quality.contains(quality) },
                { Regex("""(\d+)p""").find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    // =========================== Preferences =============================

    companion object {
        const val PREFIX_SEARCH = "id:"
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "1080"
        private val QUALITY_LIST = arrayOf("1080", "720", "480", "360")
        private const val PREF_SERVER_KEY = "preferred_server"
        private const val PREF_SERVER_DEFAULT = "StreamWish"
        private val SERVER_LIST = arrayOf("StreamTape", "Okru", "Voe", "Filemoon", "StreamWish", "DoodStream", "VidHide")
        private const val PREF_LANGUAGE_KEY = "preferred_language"
        private const val PREF_LANGUAGE_DEFAULT = "Español"
        private val LANGUAGE_LIST = arrayOf("Español", "Inglés")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SERVER_KEY
            title = "Preferred server"
            entries = SERVER_LIST
            entryValues = SERVER_LIST
            setDefaultValue(PREF_SERVER_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = QUALITY_LIST
            entryValues = QUALITY_LIST
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_LANGUAGE_KEY
            title = "Preferred language"
            entries = LANGUAGE_LIST
            entryValues = LANGUAGE_LIST
            setDefaultValue(PREF_LANGUAGE_DEFAULT)
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
