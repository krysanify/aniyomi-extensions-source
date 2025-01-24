package eu.kanade.tachiyomi.animeextension.en.dramacool

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.mixdropextractor.MixDropExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.lib.vidmolyextractor.VidMolyExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DramaCool : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val id = 76017545488916387L
    override val name = "DramaClone"

    override val baseUrl by lazy { prefClone.baseUrl }

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val prefClone = preferences.getString(PREF_DOMAIN_KEY, Clones.DEFAULT)!!
        .let { Clones.getOrBuild(it, getCustomDomain()) }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET(prefClone.getPopularList(page))

    override fun popularAnimeSelector() = prefClone.popularItemSelector

    override fun popularAnimeFromElement(element: Element) = prefClone.createPopularItem(element)

    override fun popularAnimeNextPageSelector() = prefClone.popularNextSelector

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET(prefClone.getLatestList(page))

    override fun latestUpdatesSelector() = prefClone.latestItemSelector

    override fun latestUpdatesFromElement(element: Element) = prefClone.createLatestItem(element)

    override fun latestUpdatesNextPageSelector() = prefClone.latestNextSelector

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        GET(prefClone.getSearchList(page, query, filters))

    override fun searchAnimeSelector() = prefClone.searchItemSelector

    override fun searchAnimeFromElement(element: Element) = prefClone.createSearchItem(element)

    override fun searchAnimeNextPageSelector() = prefClone.searchNextSelector

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request {
        if (anime.url.contains("-episode-") && anime.url.endsWith(".html")) {
            val doc = client.newCall(GET(baseUrl + anime.url)).execute().asJsoup()
            anime.setUrlWithoutDomain(doc.selectFirst("div.category a")!!.attr("href"))
        }
        return GET(baseUrl + anime.url)
    }

    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        document.selectFirst(prefClone.coverSelector)?.run {
            title = attr("alt")
            thumbnail_url = absUrl("src")
        }
        val info = document.selectFirst(prefClone.detailsSelector)
        prefClone.parseDramaInfo(info).let {
            description = it.description
            author = it.author
            genre = it.genre
            status = it.status
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector() = prefClone.episodeSelector

    override fun episodeFromElement(element: Element) = prefClone.createEpisode(element)

    // ============================ Video Links =============================
    override fun videoListSelector() = "ul.list-server-items li"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val iframe = document.selectFirst("iframe") ?: return emptyList()
        return prefClone.getVideoList(iframe, ::fetchVideoList, ::extractVideoList).sort()
    }

    private fun fetchVideoList(iframeUrl: String): List<Video> {
        val iframeDoc = client.newCall(GET(iframeUrl)).execute().asJsoup()
        return iframeDoc.select(videoListSelector()).flatMap(::videosFromElement)
    }

    private val doodExtractor by lazy { DoodExtractor(client) }
    private val streamwishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val streamtapeExtractor by lazy { StreamTapeExtractor(client) }
    private val vidMolyExtractor by lazy { VidMolyExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val mixDropExtractor by lazy { MixDropExtractor(client) }

    // TODO: Create a extractor for the "Standard server" thingie.
    // it'll require Synchrony or something similar, but synchrony is too slow >:(
    private fun videosFromElement(element: Element): List<Video> {
        val url = element.attr("data-video")
        val host = prefClone.mapVideoHost(element, url)
        return extractVideoList(host, url)
    }

    private fun extractVideoList(host: VideoHosts, url: String) = runCatching {
        when (host) {
            VideoHosts.VidMoly -> vidMolyExtractor.videosFromUrl(url)
            VideoHosts.VidHide -> vidHideExtractor.videosFromUrl(url)
            VideoHosts.StreamHQ -> streamwishExtractor.videosFromUrl(url, "StreamHQ")
            VideoHosts.DoodStream -> doodExtractor.videosFromUrl(url)
            VideoHosts.StreamWish -> streamwishExtractor.videosFromUrl(url)
            VideoHosts.StreamTape -> streamtapeExtractor.videosFromUrl(url)
            VideoHosts.FileLions -> streamwishExtractor.videosFromUrl(url, "FileLions")
            VideoHosts.MixDrop -> mixDropExtractor.videoFromUrl(url)
            else -> emptyList()
        }
    }.getOrElse { emptyList() }

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "Preferred Domain"
            entries = Clones.toArray()
            entryValues = entries
            setDefaultValue(Clones.DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                Toast.makeText(screen.context, "Restart app to apply $selected", Toast.LENGTH_LONG)
                    .show()
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_CUSTOM_KEY
            title = "Custom Domain"
            summary = getCustomDomain()?.let { "Moved to `$it`" }
            setOnPreferenceChangeListener { pref, newValue ->
                (newValue as String).trim().takeUnless(String::isBlank)?.let {
                    pref.summary = "Restart app to apply `$it`"
                    preferences.edit().putString(pref.key, it).commit()
                } ?: preferences.edit().remove(pref.key).commit().also {
                    pref.summary = "Restart app to clear"
                }
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_HOST_KEY
            title = "Preferred Host"
            entries = VideoHosts.values().map { it.name }.toTypedArray()
            entryValues = entries
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
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

    private fun getCustomDomain() = preferences.getString(PREF_CUSTOM_KEY, null)
        ?.takeUnless(String::isBlank)

    // ============================= Utilities ==============================
    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        val host = preferences.getString(PREF_HOST_KEY, "")!!
        return sortedWith(
            compareByDescending<Video> { it.quality.contains(quality) }
                .thenByDescending { it.quality.contains(host, true) },
        )
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "preferred_domain_base"
        private const val PREF_CUSTOM_KEY = "custom_domain"
        private const val PREF_HOST_KEY = "preferred_host"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred Quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p", "Doodstream", "StreamTape")
        private val PREF_QUALITY_VALUES = PREF_QUALITY_ENTRIES
    }
}
