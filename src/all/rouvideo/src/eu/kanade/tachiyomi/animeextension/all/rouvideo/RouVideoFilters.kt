package eu.kanade.tachiyomi.animeextension.all.rouvideo

import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LATEST_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_LIKE_KEY
import eu.kanade.tachiyomi.animeextension.all.rouvideo.RouVideo.Companion.SORT_VIEW_KEY
import eu.kanade.tachiyomi.animesource.model.AnimeFilter

internal object RouVideoFilters {

    class CategoryFilter : UriPartFilter(
        "分類",
        arrayOf(
            Pair("全部視頻", ""),
            Pair("國產AV", "國產AV"),
            Pair("自拍流出", "自拍流出"),
            Pair("探花", "探花"),
            Pair("OnlyFans", "OnlyFans"),
            Pair("日本", "日本"),
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
        val selected get() = options[state].second.takeUnless { state == 0 }
    }
}
