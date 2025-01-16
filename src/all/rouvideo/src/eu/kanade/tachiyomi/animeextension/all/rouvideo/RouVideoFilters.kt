package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LATEST_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LIKE_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_VIEW_KEY
import eu.kanade.tachiyomi.animesource.model.AnimeFilter

internal object RouVideoFilters {

    class CategoryFilter : UriPartFilter(
        "分類",
        arrayOf(
            Pair("精選", FEATURED),
            Pair("大家正在看", WATCHING),
            Pair("國產AV", "國產AV"), // ChineseAV
            Pair("自拍流出", "自拍流出"), // Selfie leaked
            Pair("探花", "探花"), // Tanhua (Flower exploration - Thám hoa - Check hàng)
            Pair("OnlyFans", "OnlyFans"),
            Pair("日本", "日本"), // JAV
            Pair("全部視頻", ALL_VIDEOS),
        ),
    )

    class SortFilter : UriPartFilter(
        "排序",
        arrayOf(
            Pair("時間", SORT_LATEST_KEY),
            Pair("觀看", SORT_VIEW_KEY),
            Pair("點贊", SORT_LIKE_KEY),
        ),
    )

    open class UriPartFilter(displayName: String, val options: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart() = options[state].second
        fun isDefault() = state == 0
    }

    const val FEATURED = "featured"
    const val WATCHING = "watching"
    const val ALL_VIDEOS = "all-videos"
}
