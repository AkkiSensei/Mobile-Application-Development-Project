package com.example.madverse

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.madverse.core.model.*
import com.example.madverse.feature.details.presentation.components.StreamingProvidersSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled in the source pass; execution is reserved for later device acceptance. */
class StreamingProviderClickTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachCategoryForwardsTheTappedProviderThroughSectionRowAndBadge() {
        val providers = ProviderType.entries.mapIndexed { index, category ->
            WatchProvider(index + 1, "Provider $index", null, category)
        }
        val clicked = mutableListOf<WatchProvider>()
        compose.setContent {
            StreamingProvidersSection(listOf(providers[0]), listOf(providers[1]), listOf(providers[2]),
                watchLink = null, onOpenWatchLink = {}, onProviderClick = { clicked += it }, onRetry = {})
        }
        providers.forEach { provider ->
            compose.onNodeWithContentDescription("${provider.providerName} streaming provider").performClick()
        }
        assertEquals(providers, clicked)
    }
}
