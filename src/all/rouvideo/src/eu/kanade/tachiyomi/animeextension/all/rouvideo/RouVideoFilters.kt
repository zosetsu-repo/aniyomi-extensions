package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LATEST_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LIKE_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_VIEW_KEY
import eu.kanade.tachiyomi.animesource.model.AnimeFilter

internal object RouVideoFilters {

    class CategoryFilter : UriPartFilter(
        "分類",
        arrayOf(
            Tag("精選", FEATURED),
            Tag("大家正在看", WATCHING),
        )
            .plus(CATEGORIES.map { Tag(it, it) })
            .plus(Tag("全部視頻", ALL_VIDEOS)),
    )

    class TagFilter(tags: Tags) : UriPartFilter(
        "標籤",
        tags,
    )

    class SortFilter : UriPartFilter(
        "排序",
        arrayOf(
            Pair("時間", SORT_LATEST_KEY),
            Pair("觀看", SORT_VIEW_KEY),
            Pair("點贊", SORT_LIKE_KEY),
        ),
    )

    open class UriPartFilter(displayName: String, private val options: Tags) :
        AnimeFilter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart() = options[state].second
        fun isEmpty() = options[state].second == ""
        fun isDefault() = state == 0
    }

    val CATEGORIES = setOf(
        "國產AV", // ChineseAV
        "麻豆傳媒", // Madou Media
        "自拍流出", // Selfie leaked
        "探花", // Tanhua (Flower exploration - Thám hoa - Check hàng)
        "OnlyFans",
        "日本", // JAV
    )

    const val FEATURED = "featured"
    const val WATCHING = "watching"
    const val ALL_VIDEOS = "all-videos"
}

typealias Tags = Array<Tag>
typealias Tag = Pair<String, String>
