package eu.kanade.tachiyomi.animeextension.en.dramacool

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import okio.ByteString.Companion.decodeBase64
import org.jsoup.nodes.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun String.undo() = decodeBase64()!!.utf8()

enum class Clones(val value: String) {
    AsianC("YXNpYW5jLmNv".undo()),
    DramaCoolTv1("ZHJhbWFjb29sdHYuY3lvdQ".undo()),
    DramaCoolTv2("d2F0Y2hhc2lhbi5jb20uaW0".undo()),
    DramaCoolTv3("dmlld2FzaWFuLmxvbA".undo()),
    DramaNice("ZHJhbWFuaWNlLmN5b3U".undo()),
    MyAsianTv1("bXlhc2lhbnR2LmN2".undo()),
    MyAsianTv2("bXlhc2lhbnR2LnJlc3Q".undo()),
    ;

    companion object {
        val DEFAULT = AsianC.value
        fun toArray() = values().map { it.value }.sorted().toTypedArray()
        fun getOrBuild(id: String, domain: String? = null): CloneSite {
            val clone = values().firstOrNull { id == it.value } ?: AsianC
            return when (clone) {
                DramaCoolTv1 -> CloneSite.DramaCoolTv1
                DramaCoolTv2 -> CloneSite.DramaCoolTv2
                DramaCoolTv3 -> CloneSite.DramaCoolTv3
                DramaNice -> CloneSite.DramaNice(domain)
                MyAsianTv1 -> CloneSite.MyAsianTv1
                MyAsianTv2 -> CloneSite.MyAsianTv2
                else -> CloneSite.AsianC(domain)
            }
        }
    }
}

enum class VideoHosts { DoodStream, FileLions, MixDrop, StreamHG, StreamTape, StreamWish, VidHide, VidMoly, Unknown }

sealed class CloneSite(domain: String) {
    open class DramaCoolTv(d: Clones) : CloneSite(d.value) {
        override val popularItemSelector = "ul.list-episode-item li a"
        override val popularNextSelector = "li a.next"
        override val searchNextSelector = "ul.page-numbers li:has(> span.current) + li a.page-numbers"
        override val detailsSelector = "div.info"
        override val episodeSelector = "ul.all-episode li a"

        override fun getPopularList(page: Int) =
            if (1 == page) {
                "$baseUrl/most-popular-drama/"
            } else {
                "$baseUrl/most-popular-drama/page/$page"
            }

        override fun getLatestList(page: Int) = if (1 == page) baseUrl else "$baseUrl/page/$page"

        override fun getSearchList(page: Int, query: String, filters: AnimeFilterList) =
            if (1 == page) {
                "$baseUrl/?type=movies&s=$query"
            } else {
                "$baseUrl/page/$page/?type=movies&s=$query"
            }

        override fun createPopularItem(element: Element) = SAnime.create().apply {
            url = element.attr("href").getLinkAsSeries()
            thumbnail_url = element.selectFirst("img")?.attr("data-original")
            title = element.selectFirst(".title").textNotBlank() ?: "Untitled"
        }

        override fun parseDramaInfo(element: Element?) = element?.toDramaInfo(
            "p:contains(Description) ~ p:not(:has(span))",
            "p:contains(Genre:)",
            "p:contains(Status:)",
        ) ?: DramaInfo()

        override fun createEpisode(element: Element) = SEpisode.create().apply(
            link = element.attr("href"),
            type = element.selectFirst(".type").textNotBlank() ?: "RAW",
            title = element.selectFirst(".title").textNotBlank(),
            time = element.selectFirst(".time"),
        )

        override fun getVideoList(
            element: Element,
            onRedirect: (String) -> List<Video>,
            onEmbedded: (VideoHosts, String) -> List<Video>,
        ): List<Video> = element.attr("data-src").toVideoList(onEmbedded, onRedirect)

        override fun mapVideoHost(element: Element, url: String) = element.toVideoHost()
    }

    object DramaCoolTv1 : DramaCoolTv(Clones.DramaCoolTv1)

    object DramaCoolTv2 : DramaCoolTv(Clones.DramaCoolTv2)

    object DramaCoolTv3 : DramaCoolTv(Clones.DramaCoolTv3) {
        override fun createPopularItem(element: Element) = super.createPopularItem(element)
            .also { it.url = it.url.removePrefix("/series") }
    }

    open class DramaNice(id: String?) : CloneSite(id ?: Clones.DramaNice.value) {
        private val dotWsLatestSelector = "#drama ul.box li a.mask"
        private val dotWsSearchSelector = "ul.list-thumb li .post-thumbnail a"
        private val dotWsNextSelector = ".nav-links a.next"

        final override val popularItemSelector = "ul.items li .img a"
        final override val latestItemSelector = "$popularItemSelector,$dotWsLatestSelector"
        final override val searchItemSelector = "$popularItemSelector,$dotWsSearchSelector"
        final override val popularNextSelector = "li.next a"
        final override val latestNextSelector = "li a.next"
        final override val searchNextSelector = "li a.next,$dotWsNextSelector"
        final override val detailsSelector = "div.info_right"
        final override val episodeSelector = "ul.list_episode li a"

        override fun getPopularList(page: Int) =
            if (1 == page) {
                "$baseUrl/popular-dramas/"
            } else {
                "$baseUrl/popular-dramas/page/$page"
            }

        override fun getLatestList(page: Int) = if (1 == page) baseUrl else "$baseUrl/page/$page"

        override fun getSearchList(page: Int, query: String, filters: AnimeFilterList) =
            if (1 == page) {
                "$baseUrl/?s=$query"
            } else {
                "$baseUrl/page/$page/?s=$query"
            }

        override fun createPopularItem(element: Element) = SAnime.create().apply {
            url = element.attr("href").getLinkAsSeries()
            title = element.selectFirst("img")?.run {
                thumbnail_url = attr("data-src")
                    .ifEmpty { attr("data-original") }
                    .ifEmpty { attr("src") }
                attr("alt")
                    .ifEmpty { attr("title") }
                    .ifEmpty { null }
            } ?: element.attr("title")
                .ifEmpty { "Untitled" }
        }

        override fun parseDramaInfo(element: Element?) = element?.toDramaInfo(
            "p:contains(Description) ~ p:not(:has(span))",
            "p:contains(Genre:)",
            "p:contains(Status:)",
        ) ?: DramaInfo()

        override fun createEpisode(element: Element) = SEpisode.create().apply(
            link = element.attr("href"),
            type = if (element.`is`(":has(.SUB)")) "SUB" else "RAW",
            title = element.attr("title"),
            time = null,
        )

        override fun getVideoList(
            element: Element,
            onRedirect: (String) -> List<Video>,
            onEmbedded: (VideoHosts, String) -> List<Video>,
        ): List<Video> = with(element.parents().select("#w-server .serverslist")) {
            if (isEmpty()) {
                element.attr("data-src").toVideoList(onEmbedded, onRedirect)
            } else distinctBy { it.attr("data-server") }.flatMap {
                it.attr("data-server").toVideoList(onEmbedded, onRedirect)
            }
        }

        override fun mapVideoHost(element: Element, url: String) = element.toVideoHost()
    }

    open class MyAsianTv(d: Clones) : CloneSite(d.value) {
        final override val popularItemSelector = "ul.items > li > a"
        final override val popularNextSelector = "ul.page-numbers li a.next"
        final override val searchNextSelector = "ul.pagination li.selected + li a"
        final override val detailsSelector = "div.movie"
        final override val episodeSelector = "ul.list-episode li a"

        override fun getPopularList(page: Int): String = throw UnsupportedOperationException()
        override fun getLatestList(page: Int): String = throw UnsupportedOperationException()
        override fun getSearchList(page: Int, query: String, filters: AnimeFilterList) =
            if (1 == page) {
                "$baseUrl/?s=$query"
            } else {
                "$baseUrl/search/$query/page/$page"
            }

        override fun createPopularItem(element: Element) = SAnime.create().apply {
            url = element.attr("href").getLinkAsSeries()
            element.selectFirst("img")?.run {
                thumbnail_url = attr("src")
                title = attr("alt").ifBlank { "Untitled" }
            }
        }

        override fun parseDramaInfo(element: Element?) = element?.toDramaInfo(
            "h3:contains(Plot) + div.info p",
            "p:contains(Genre) span",
            "p:contains(Status) span",
        ) ?: DramaInfo()

        override fun createEpisode(element: Element) = SEpisode.create().apply(
            link = element.attr("href"),
            type = element.parent()?.previousElementSibling().textNotBlank() ?: "RAW",
            title = element.attr("title"),
            time = element.parent()?.nextElementSibling(),
        )

        override fun getVideoList(
            element: Element,
            onRedirect: (String) -> List<Video>,
            onEmbedded: (VideoHosts, String) -> List<Video>,
        ): List<Video> = element.attr("data-src").toVideoList(onEmbedded, onRedirect)

        override fun mapVideoHost(element: Element, url: String) = element.toVideoHost()

        protected fun getDramaList(page: Int, order: Int, prefix: String = "drama") =
            if (1 == page) {
                "$baseUrl/$prefix/?selOrder=$order"
            } else {
                "$baseUrl/$prefix/page/$page/?selOrder=$order"
            }
    }

    object MyAsianTv1 : MyAsianTv(Clones.MyAsianTv1) {
        override fun getPopularList(page: Int) = getDramaList(page, 4)
        override fun getLatestList(page: Int) = getDramaList(page, 1)
    }

    object MyAsianTv2 : MyAsianTv(Clones.MyAsianTv2) {
        override fun getPopularList(page: Int) = getDramaList(page, 4, "drama-8")
        override fun getLatestList(page: Int) = getDramaList(page, 1, "drama-8")
    }

    open class AsianC(id: String?) : CloneSite(id ?: Clones.AsianC.value) {
        override val popularItemSelector = "ul.list-episode-item li a"
        override val latestItemSelector = "ul.switch-block a"
        override val popularNextSelector = "li.next a"
        override val detailsSelector = "div.info"
        override val episodeSelector = "ul.all-episode li a"

        override fun getPopularList(page: Int) = "$baseUrl/most-popular-drama?page=$page"

        override fun getLatestList(page: Int) = "$baseUrl/recently-added?page=$page"

        override fun getSearchList(page: Int, query: String, filters: AnimeFilterList) =
            "$baseUrl/search?keyword=$query&page=$page"

        override fun createPopularItem(element: Element) = SAnime.create().apply {
            url = element.attr("href").getUrlWithoutDomain()
            thumbnail_url = element.selectFirst("img")?.attr("data-original")?.replace(" ", "%20")
            title = element.selectFirst("h3")?.text() ?: "Serie"
        }

        override fun parseDramaInfo(element: Element?) = element?.run {
            DramaInfo(
                description = select("p:contains(Description) ~ p:not(:has(span))").eachText()
                    .joinToString("\n")
                    .takeUnless(String::isBlank),
                author = selectFirst("p:contains(Original Network:) > a")?.text(),
                genre = select("p:contains(Genre:) > a").joinToString { it.text() }.takeUnless(String::isBlank),
                status = selectFirst("p:contains(Status) a").textToStatus(),
            )
        } ?: DramaInfo()

        override fun createEpisode(element: Element) = SEpisode.create().apply {
            url = element.attr("href").getUrlWithoutDomain()
            val epNum = element.selectFirst("h3")!!.text().substringAfterLast("Episode ")
            val type = element.selectFirst("span.type")?.text() ?: "RAW"
            name = "$type: Episode $epNum".trimEnd()
            episode_number = when {
                epNum.isNotEmpty() -> epNum.toFloatOrNull() ?: 1F
                else -> 1F
            }
            date_upload = element.selectFirst("span.time").textToDate()
        }

        override fun getVideoList(
            element: Element,
            onRedirect: (String) -> List<Video>,
            onEmbedded: (VideoHosts, String) -> List<Video>,
        ): List<Video> = with(element.absUrl("src")) {
            if (contains("/embed/")) {
                onEmbedded(VideoHosts.StreamHG, this)
            } else {
                onRedirect(this)
            }
        }

        override fun mapVideoHost(element: Element, url: String) = when {
            url.contains("dood") -> VideoHosts.DoodStream
            url.contains("dwish") -> VideoHosts.StreamWish
            url.contains("streamtape") -> VideoHosts.StreamTape
            else -> VideoHosts.Unknown
        }
    }

    val baseUrl = "https://$domain"

    abstract val popularItemSelector: String
    open val latestItemSelector: String
        get() = popularItemSelector
    open val searchItemSelector: String
        get() = popularItemSelector

    abstract val popularNextSelector: String
    open val latestNextSelector: String
        get() = popularNextSelector
    open val searchNextSelector: String
        get() = popularNextSelector

    open val coverSelector = "div.img img"
    abstract val detailsSelector: String
    abstract val episodeSelector: String

    abstract fun getPopularList(page: Int): String
    abstract fun createPopularItem(element: Element): SAnime

    abstract fun getLatestList(page: Int): String
    open fun createLatestItem(element: Element) = createPopularItem(element)

    abstract fun getSearchList(page: Int, query: String, filters: AnimeFilterList): String
    open fun createSearchItem(element: Element) = createPopularItem(element)

    abstract fun parseDramaInfo(element: Element?): DramaInfo

    abstract fun createEpisode(element: Element): SEpisode

    abstract fun getVideoList(
        element: Element,
        onRedirect: (String) -> List<Video>,
        onEmbedded: (VideoHosts, String) -> List<Video>,
    ): List<Video>

    abstract fun mapVideoHost(element: Element, url: String): VideoHosts
}

data class DramaInfo(
    val description: String? = null,
    val author: String? = null,
    val genre: String? = null,
    val status: Int = SAnime.UNKNOWN,
)

private fun Element.toDramaInfo(storySelector: String, genreSelector: String, statusSelector: String) = run {
    val story = select(storySelector).eachText().joinToString("\n")
    val source = story.substringAfterLast("(Source:", ")").substringBefore(")")
    DramaInfo(
        description = story.takeUnless(String::isBlank),
        author = source.substringBeforeLast("||", source.substringBeforeLast(";"))
            .substringAfter("=").trim(),
        genre = selectFirst(genreSelector).textNotBlank(),
        status = selectFirst(statusSelector).textToStatus(),
    )
}

private val EPISODE_REGEX = Regex("""\b(Ep|Episode)\b (\d+)(\.\d+)?""")

private fun SEpisode.apply(link: String, type: String, title: String?, time: Element?) = apply {
    val episode = EPISODE_REGEX.find(title.orEmpty())
        ?.run { groupValues[2] + groupValues[3] } ?: "0"
    url = link.getUrlWithoutDomain()
    name = if ("0" == episode) "$type Movie" else "$type Episode $episode"
    episode_number = episode.toFloatOrNull() ?: 1F
    date_upload = time.textToDate()
}

private fun String.getUrlWithoutDomain() = URI.create(this).run {
    val url = query?.let { "$path?$it" } ?: path
    fragment?.let { "$url#$it" } ?: url
}

private fun String.getLinkAsSeries() = with(getUrlWithoutDomain()) {
    if (contains("/series/")) this else substringBeforeLast("-ep-").prependIndent("/series")
}

private fun Element?.textNotBlank() = if (null == this) null else ownText().takeUnless(String::isBlank)

private fun Element?.textToStatus(): Int = when (this?.ownText()?.trim()) {
    "Ongoing" -> SAnime.ONGOING
    "Completed" -> SAnime.COMPLETED
    else -> SAnime.UNKNOWN
}

private val DATE_FORMATTER by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
}

private fun Element?.textToDate(): Long = this?.ownText()?.run {
    runCatching { DATE_FORMATTER.parse(this)?.time }.getOrElse {
        System.currentTimeMillis() - when {
            contains(" second") -> substringBefore(' ').toInt().seconds
            contains(" minute") -> substringBefore(' ').toInt().minutes
            contains(" hour") -> substringBefore(' ').toInt().hours
            contains(" day") -> substringBefore(' ').toInt().days
            contains(" week") -> (substringBefore(' ').toInt() * 7).days
            contains(" month") -> (substringBefore(' ').toInt() * 30).days
            contains(" year") -> (substringBefore(' ').toInt() * 365).days
            else -> 0.milliseconds
        }.inWholeMilliseconds
    }
} ?: 0L

private fun String.toVideoList(
    onEmbedded: (VideoHosts, String) -> List<Video>,
    onRedirect: (String) -> List<Video>,
) = when {
    contains("vidmoly") -> onEmbedded(VideoHosts.VidMoly, this)
    contains("iplayerhls") -> onEmbedded(VideoHosts.StreamHG, this)
    contains("dood") -> onEmbedded(VideoHosts.DoodStream, this)
    contains("dwish") -> onEmbedded(VideoHosts.StreamWish, this)
    contains("streamtape") -> onEmbedded(VideoHosts.StreamTape, this)
    contains("sbplay2") -> onEmbedded(VideoHosts.StreamTape, this)
    contains("mixdrop") -> onEmbedded(VideoHosts.MixDrop, this)
    contains("dlions") -> onEmbedded(VideoHosts.FileLions, this)
    contains("/embed/") -> onEmbedded(VideoHosts.StreamWish, this)
    startsWith("//") -> onRedirect("https:$this")
    else -> onRedirect(this)
}

private fun Element.toVideoHost() = when (attr("data-provider")) {
    "vidmoly" -> VideoHosts.VidMoly
    "streamhg" -> VideoHosts.StreamHG
    "vidhide" -> VideoHosts.VidHide
    "streamwish" -> VideoHosts.StreamWish
    "doodstream" -> VideoHosts.DoodStream
    "streamtape" -> VideoHosts.StreamTape
    else -> VideoHosts.Unknown
}
