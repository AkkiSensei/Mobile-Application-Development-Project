package com.example.madverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.WatchProvider

/**
 * Standardized streaming, rent, or buy provider card badge.
 *
 * Displays TMDB official provider logos with high-contrast obsidian styling,
 * resilient fallback initials, and accessibility descriptions.
 */
@Composable
fun WatchProviderBadge(
    provider: WatchProvider,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(size + 14.dp)
            .semantics {
                role = Role.Button
                contentDescription = "${provider.providerName} streaming provider"
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(
                            role = Role.Button,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Container
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianSurfaceVariant)
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!provider.logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = provider.logoUrl,
                    contentDescription = provider.providerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CinematicAmber.copy(alpha = 0.6f),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        WatchProviderBadgeFallback(provider.providerName)
                    }
                )
            } else {
                WatchProviderBadgeFallback(provider.providerName)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Provider Name Label
        Text(
            text = provider.providerName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = ContentHighEmphasis,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Fallback badge when provider logo image fails to load or is unavailable.
 */
@Composable
private fun WatchProviderBadgeFallback(providerName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        if (providerName.isNotBlank()) {
            Text(
                text = providerName.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CinematicAmber
            )
        } else {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = ContentLowEmphasis,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Horizontally scrollable row of streaming providers.
 */
@Composable
fun WatchProviderBadgeRow(
    providers: List<WatchProvider>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    onProviderClick: ((WatchProvider) -> Unit)? = null
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(providers, key = { it.providerId }) { provider ->
            WatchProviderBadge(
                provider = provider,
                onClick = onProviderClick?.let { { it(provider) } }
            )
        }
    }
}
