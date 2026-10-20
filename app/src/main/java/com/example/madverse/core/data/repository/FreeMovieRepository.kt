package com.example.madverse.core.data.repository

import com.example.madverse.core.model.*
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeMovieRepository internal constructor(entries: List<FreeMovie>) {
    private val entries = entries.toList().also(::validateCatalogue)

    @Inject constructor() : this(listOf(
        FreeMovie(
            catalogueId = "star-wreck", title = "Star Wreck: In the Pirkinning", videoId = "RHAPXlVq5lk",
            publisher = "Energia Productions (@emperorpirk)",
            description = "A complete Finnish science-fiction parody, approximately 103 minutes, with multilingual subtitles.",
            format = FilmFormat.FEATURE_FILM, authorization = AuthorizationBasis.PUBLISHER_CC_BY_NC_ND,
            sourceUrl = "https://www.youtube.com/watch?v=RHAPXlVq5lk",
            rightsUrl = "https://www.youtube.com/watch?v=RHAPXlVq5lk",
            attribution = "Star Wreck: In the Pirkinning — Energia Productions; Timo Vuorensola / Samuli Torssonen. CC BY-NC-ND (publisher declaration).",
            verifiedOn = "2026-10-14",
            verificationNotes = "Production channel identifies the complete feature and declares CC BY-NC-ND in its description. YouTube metadata: 6195 seconds, playableInEmbed=true; actual device playback pending.",
            regionNotes = "Noncommercial, unmodified presentation only. YouTube reported embeddable at verification; India device playback and regional availability remain pending. Review rights before any commercial release.",
            licenseUrl = "https://creativecommons.org/licenses/by-nd-nc/1.0/fi/deed.en"
        ),
        film("big-buck-bunny", "Big Buck Bunny", "aqz-KE-bpKQ", "big-buck-bunny",
            "https://peach.blender.org/about/", "A gentle rabbit stands up to a group of forest bullies.",
            "© 2008 Blender Foundation / www.bigbuckbunny.org"),
        film("sintel", "Sintel", "eRsGyueVLvQ", "sintel",
            "https://durian.blender.org/sharing/", "An animated fantasy short following a young traveller and a dragon.",
            "© Blender Foundation / durian.blender.org"),
        film("tears-of-steel", "Tears of Steel", "R6MlUcmOul8", "tears-of-steel",
            "https://mango.blender.org/sharing/", "A science-fiction short combining live action with visual effects.",
            "© Blender Foundation / mango.blender.org")
    ))

    fun getMovies(): List<FreeMovie> = entries.toList()
    fun find(catalogueId: String): FreeMovie? = entries.singleOrNull { it.catalogueId == catalogueId }

    companion object {
        private fun film(id: String, title: String, videoId: String, project: String, rights: String,
            description: String, attribution: String) = FreeMovie(
            catalogueId = id, title = title, videoId = videoId, publisher = "Blender (@BlenderOfficial)",
            description = description, format = FilmFormat.SHORT_FILM, authorization = AuthorizationBasis.PUBLISHER_CC_BY_3,
            sourceUrl = "https://studio.blender.org/projects/$project/", rightsUrl = rights, attribution = attribution,
            verifiedOn = "2026-10-14",
            verificationNotes = "Official project links to this complete film; publisher CC BY 3.0 license and YouTube oEmbed identity checked. Device playback pending.",
            regionNotes = "Publisher's license has no stated India exclusion. YouTube availability and embedded playback in India still need device validation."
        )

        fun validateCatalogue(entries: List<FreeMovie>) {
            require(entries.map { it.catalogueId }.distinct().size == entries.size) { "Duplicate catalogue IDs" }
            require(entries.map { it.videoId }.distinct().size == entries.size) { "Duplicate video IDs" }
            entries.forEach {
                require(it.catalogueId.matches(Regex("[a-z0-9-]+")))
                require(YouTubeUrlSanitizer.isValidVideoId(it.videoId))
                require(it.publisher.isNotBlank() && it.attribution.isNotBlank() && it.verificationNotes.isNotBlank())
                require(it.sourceUrl.startsWith("https://") && it.rightsUrl.startsWith("https://"))
                require(it.verifiedOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            }
        }
    }
}
