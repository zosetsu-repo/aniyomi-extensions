package eu.kanade.tachiyomi.animeextension.all.streamingcommunity

import eu.kanade.tachiyomi.animeextension.all.streamingcommunity.SingleShowResponse.SingleShowObject.ShowObject.GenreObject
import kotlinx.serialization.Serializable

@Serializable
data class ShowsResponse(
    val props: PropObject,
)

@Serializable
data class PropObject(
    val titles: List<TitleObject>,
    val scws_url: String, // https://vixcloud.co
    val cdn_url: String, // https://cdn.streamingunity.to
    val browseMoreApiRoute: String, // "https://streamingunity.to/api/browse/top10?type=movie"
) {
    @Serializable
    data class TitleObject(
        val id: Int,
        val slug: String,
        val name: String,
        val images: List<ImageObject>,
        val score: String, // "7.7"
        val last_air_date: String, // "2003-09-06"
    ) {
        @Serializable
        data class ImageObject(
            val filename: String,
            val type: String, // poster, cover, background, logo, cover_mobile
        )
    }
}

@Serializable
data class SingleShowResponse(
    val props: SingleShowObject,
    val version: String? = null,
) {
    @Serializable
    data class SingleShowObject(
        val title: ShowObject? = null,
        val loadedSeason: LoadedSeasonObject? = null,
    ) {
        @Serializable
        data class ShowObject(
            val id: Int,
            val plot: String? = null,
            val status: String? = null,
            val seasons: List<SeasonObject>,
            val genres: List<GenreObject>? = null,
        ) {
            @Serializable
            data class SeasonObject(
                val id: Int,
                val number: Int,
            )

            @Serializable
            data class GenreObject(
                val name: String,
            )
        }

        @Serializable
        data class LoadedSeasonObject(
            val id: Int,
            val episodes: List<EpisodeObject>,
        ) {
            @Serializable
            data class EpisodeObject(
                val id: Int,
                val number: Int,
                val name: String,
            )
        }
    }
}

@Serializable
data class SearchAPIResponse(
    val data: List<PropObject.TitleObject>,
)

@Serializable
data class GenreAPIResponse(
    val titles: List<PropObject.TitleObject>,
)

@Serializable
data class VideoResponse(
    val props: VideoPropObject,
) {
    @Serializable
    data class VideoPropObject(
        val embedUrl: String,
    )
}
