package eu.kanade.tachiyomi.animeextension.all.streamingcommunity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.lib.i18n.Intl

internal object StreamingCommunityFilters {

    class BrowseGenreFilter(val uri: String, intl: Intl) : UriPartFilter(
        intl["browse_genres"],
        arrayOf(
            Pair("<select>", ""),
            Pair(intl["Action"], "Action"),
            Pair(intl["Adventure"], "Adventure"),
            Pair(intl["Animation"], "Animation"),
            Pair(intl["Comedy"], "Comedy"),
            Pair(intl["Crime"], "Crime"),
            Pair(intl["Documentary"], "Documentary"),
            Pair(intl["Drama"], "Drama"),
            Pair(intl["Family"], "Family"),
            Pair(intl["Fantasy"], "Fantasy"),
            Pair(intl["History"], "History"),
            Pair(intl["Horror"], "Horror"),
            Pair(intl["Koreandrama"], "Korean drama"),
            Pair(intl["Music"], "Music"),
            Pair(intl["Mystery"], "Mystery"),
            Pair(intl["News"], "News"),
            Pair(intl["Romance"], "Romance"),
            Pair(intl["ScienceFiction"], "Science Fiction"),
            Pair(intl["Thriller"], "Thriller"),
            Pair(intl["TVMovie"], "TV Movie"),
            Pair(intl["War"], "War"),
            Pair(intl["Western"], "Western"),
        ),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
        fun isEmpty() = vals[state].second == ""
        fun isDefault() = state == 0
    }
}
