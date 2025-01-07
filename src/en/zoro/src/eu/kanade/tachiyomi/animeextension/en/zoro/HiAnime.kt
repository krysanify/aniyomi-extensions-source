package eu.kanade.tachiyomi.animeextension.en.zoro

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.megacloudextractor.MegaCloudExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.vidsrcextractor.VidsrcExtractor
import eu.kanade.tachiyomi.multisrc.zorotheme.ZoroTheme

class HiAnime : ZoroTheme(
    "en",
    "HiAnime",
    "hianime.to",
    arrayOf("hianime.to", "hianime.nz", "hianime.mn", "hianime.sx", "aniwatchtv.to"),
    hosterNames = listOf(
        "HD-1",
        "HD-2",
        "StreamTape",
        "VidSrc",
    ),
) {
    override val id = 6706411382606718900L

    override val ajaxRoute = "/v2"

    private val streamtapeExtractor by lazy { StreamTapeExtractor(client) }
    private val megaCloudExtractor by lazy { MegaCloudExtractor(client, headers, preferences) }
    private val vidsrcExtractor by lazy { VidsrcExtractor(client, headers) }

    override fun extractVideo(server: VideoData): List<Video> {
        return when (server.name) {
            "StreamTape" -> {
                streamtapeExtractor.videoFromUrl(server.link, "Streamtape - ${server.type}")
                    ?.let(::listOf)
                    ?: emptyList()
            }
            "HD-1", "HD-2", "MegaCloud" -> megaCloudExtractor.getVideosFromUrl(server.link, server.type, server.name)
            "VidSrc" -> vidsrcExtractor.videosFromUrl(server.link, "VidSrc - ${server.type}")
            else -> emptyList()
        }
    }
}
