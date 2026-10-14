package com.example.madverse

import com.example.madverse.core.network.model.VideoDto
import org.junit.Assert.*
import org.junit.Test

class VideoSourceValidationTest {
    private val dto = VideoDto("1", "M7lc1UVf-VE", "Trailer", "YouTube", "Trailer", true)
    @Test fun absentOrBlankSitesAreNotInvented() {
        listOf(null, "", " ").forEach { assertNull(dto.copy(site = it).toDomain()) }
    }
    @Test fun absentBlankMalformedYouTubeKeysAreRejected() {
        listOf(null, "", " ", "short", "https://youtu.be/M7lc1UVf-VE", "M7lc1UVf-VEextra").forEach {
            assertNull(dto.copy(key = it).toDomain())
        }
    }
    @Test fun unsupportedSourceIsPreservedWithoutAYouTubeDestination() {
        val video = dto.copy(site = "Vimeo", key = "123456789").toDomain()!!
        assertEquals("Vimeo", video.site)
        assertFalse(video.isPlayableInApp)
        assertEquals("", video.youtubeWatchUrl)
    }
    @Test fun missingTypeIsUnknownNotATrailer() {
        listOf(null, "", " ").forEach {
            val video = dto.copy(type = it).toDomain()!!
            assertEquals("Unknown", video.type)
            assertFalse(video.isEmbeddableYouTubeTrailer)
        }
    }
    @Test fun normalizedYouTubeMetadataRemainsEligibleWithoutGuaranteeingEmbedding() {
        val video = dto.copy(site = " youtube ", key = " M7lc1UVf-VE ", type = " Teaser ").toDomain()!!
        assertTrue(video.isPlayableInApp)
        assertFalse(video.isAuthorizedFullLength)
    }
}
