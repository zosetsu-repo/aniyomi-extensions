package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

internal object RouVideoDto {
    @Serializable
    data class VideoList(
        val props: PropsObject,
    ) {
        @Serializable
        data class PropsObject(
            val pageProps: PagePropsObject,
        ) {
            @Serializable
            data class PagePropsObject(
                val order: String, // createdAt...
                val videos: List<Video>,
                val pageNum: Int,
                val totalPage: Int,
                val totalVideoNum: Int,
                val tagsForCNAV: List<Tag>, // 國產AV
                val tags91: List<Tag>, // 探花
            )
        }
    }

    @Serializable
    data class VideoDetails(
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
            )
        }
    }

    @Serializable
    data class Video(
        val id: String,
        @SerialName("vid")
        val code: String?,
        val name: String,
        val description: String?,
        val ref: String?,
        val tags: List<String>,
        val createdAt: String, // "2025-01-14T23:18:27.933Z"
        val viewCount: Int,
        val likeCount: Int?, // not available in relatedVideos
        val duration: Float, // in seconds
        val coverImageUrl: String,
        val nameZh: String?,
        val tagZh: List<String>?,
        val sources: List<Source>?, // not available in details
    ) {
        private val desc = StringBuilder().apply {
            sources?.first()?.let { append("Resolution: ${it.resolution}p\n") }
            append("Duration: ${formatDuration(duration.toInt())}\n")
            append("View: $viewCount")
            likeCount?.let { append(" - Like: $likeCount") }
            ref?.let { append("\nOrigin: $it") }
            description?.let { append("\n\n$description") }
        }.toString()

        private val majorCategory = tags.first()

        fun toSAnime(): SAnime = SAnime.create().apply {
            url = id
            title = name
            thumbnail_url = coverImageUrl
            artist = majorCategory
            author = majorCategory
            description = desc
            genre = (listOfNotNull(code) + tags).joinToString()
            status = SAnime.COMPLETED
            initialized = true
        }

        fun toEpisode(): SEpisode = SEpisode.create().apply {
            name = id
            url = id
            date_upload = createdAt.toDate()
            episode_number = 1f
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
    data class Tag(
        @SerialName("id")
        val name: String,
        val count: Int,
        val parent: String,
        val level: Int, // usually 0
    )

    @Serializable
    data class VideoData(
        val video: VideoObject,
    ) {
        @Serializable
        data class VideoObject(
            val thumbVTTUrl: String,
            val videoUrl: String,
        )
    }

    /* Not available in details */
    @Serializable
    data class Source(
        val id: String?, // not available in relatedVideos
        val videoId: String?, // not available in relatedVideos
        val resolution: Int,
        val folder: String?, // not available in relatedVideos
    )

    private val DATE_FORMATTER by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }.getOrNull() ?: 0L
    }
}
