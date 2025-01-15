package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RouVideoDto(
    val data: List<AnimeObject>,
) {
    @Serializable
    data class AnimeObject(
        @SerialName("_id")
        val id: String,
        val title: String,
        val link: String,
        val posterImage: ImageObject,
    ) {
        @Serializable
        data class ImageObject(
            val original: String? = null,
            val large: String? = null,
            val medium: String? = null,
            val small: String? = null,
        )

        fun toSAnime(json: Json): SAnime = SAnime.create().apply {
            title = this@AnimeObject.title
            thumbnail_url = posterImage.original ?: posterImage.large ?: posterImage.medium ?: posterImage.small ?: ""
            url = json.encodeToString(LinkData(slug = link, id = id))
        }
    }
}

@Serializable
data class LinkData(
    val slug: String,
    val id: String,
)

@Serializable
data class AnimeDetails(
    val props: PropsObject,
) {
    @Serializable
    data class PropsObject(
        val pageProps: PagePropsObject,
    ) {
        @Serializable
        data class PagePropsObject(
            val video: Video,
            val relatedVideos: List<Video>,
        ) {
            @Serializable
            data class Video(
                val id: String,
                @SerialName("vid")
                val code: String?,
                val name: String,
                val description: String?,
                val ref: String?,
                val tags: List<String>?,
                val createdAt: String, // "2025-01-14T23:18:27.933Z"
                val viewCount: Int,
                val likeCount: Int?,
                val duration: Float, // in seconds
                val coverImageUrl: String,
                val nameZh: String,
                val tagZh: List<String?>?,
            ) {
                private val desc = StringBuilder().apply {
                    append("View: $viewCount")
                    likeCount?.let { append(" - Like: $likeCount") }
                    append("\nDuration: ${formatDuration(duration.toInt())}")
                    ref?.let { append("\nOrigin: $it") }
                    description?.let { append("\n\n$description") }
                }.toString()
                fun toSAnime(): SAnime = SAnime.create().apply {
                    title = name
                    thumbnail_url = coverImageUrl
                    description = desc
                    genre = (listOfNotNull(code) + tags.orEmpty()).joinToString()
                    status = SAnime.COMPLETED
//                    initialized = true
                }
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val duration = seconds.seconds
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes % 60
    val remainingSeconds = duration.inWholeSeconds % 60

    return "$hours:$minutes:$remainingSeconds"
}

@Serializable
data class EpisodeListResponse(
    val data: List<EpisodeObject>,
) {
    @Serializable
    data class EpisodeObject(
        val uid: String,
        val origin: String,
        val number: String? = null,
        val title: String? = null,
    ) {
        fun toSEpisode(): SEpisode = SEpisode.create().apply {
            episode_number = number?.toFloatOrNull() ?: 1F
            name = (number?.let { "Ep. $number" } ?: "Episode") + (title?.let { " - $it" } ?: "")
            url = "/watch/$uid?origin=$origin"
        }
    }
}

@Serializable
data class VideoData(
    val props: PropsObject,
) {
    @Serializable
    data class PropsObject(
        val pageProps: PagePropsObject,
    ) {
        @Serializable
        data class PagePropsObject(
            val subtitles: List<SubtitleObject>? = null,
            val animeData: AnimeDataObject,
            val episode: EpisodeObject,
        ) {
            @Serializable
            data class SubtitleObject(
                val src: String,
                val label: String,
            )

            @Serializable
            data class AnimeDataObject(
                val title: String,
            )

            @Serializable
            data class EpisodeObject(
                val number: String,
            )
        }
    }
}

@Serializable
data class VideoList(
    val directUrl: List<VideoObject>? = null,
    val message: String? = null,
) {
    @Serializable
    data class VideoObject(
        val src: String,
        val label: String,
    )
}
