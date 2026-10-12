package com.example.madverse.feature.details.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.madverse.core.designsystem.components.WatchProviderBadge
import com.example.madverse.core.designsystem.components.WatchProviderBadgeRow
import com.example.madverse.core.model.WatchProvider

/**
 * Delegating composable preserving full backward compatibility with MovieDetails callers.
 * Reuses the standardized [WatchProviderBadge] from the core design system.
 */
@Composable
fun WatchProviderItem(
    provider: WatchProvider,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null
) {
    WatchProviderBadge(
        provider = provider,
        modifier = modifier,
        size = size,
        onClick = onClick
    )
}

/**
 * Delegating composable preserving full backward compatibility with MovieDetails callers.
 * Reuses the standardized [WatchProviderBadgeRow] from the core design system.
 */
@Composable
fun WatchProviderRow(
    providers: List<WatchProvider>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    onProviderClick: ((WatchProvider) -> Unit)? = null
) {
    WatchProviderBadgeRow(
        providers = providers,
        modifier = modifier,
        contentPadding = contentPadding,
        onProviderClick = onProviderClick
    )
}
