package com.example.madverse.feature.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.madverse.core.common.config.RegionConfig
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.WatchProvider

/**
 * Segmented Watch Providers section displaying subscription stream, rent, and buy availability
 * with country indicator and TMDB/JustWatch attribution link.
 */
@Composable
fun StreamingProvidersSection(
    flatrate: List<WatchProvider>,
    rent: List<WatchProvider>,
    buy: List<WatchProvider>,
    watchLink: String?,
    countryCode: String = RegionConfig.DEFAULT_COUNTRY_CODE,
    onOpenWatchLink: (String) -> Unit,
    onProviderClick: (WatchProvider) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRegionClick: (() -> Unit)? = null
) {
    val regionOption = RegionConfig.SUPPORTED_REGIONS.firstOrNull { it.countryCode.equals(countryCode, ignoreCase = true) }
    val regionLabel = regionOption?.let { "${it.displayName} ${it.flagEmoji}" } ?: countryCode
    val hasProviders = flatrate.isNotEmpty() || rent.isNotEmpty() || buy.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Section Header with Region indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = CinematicAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Where to Watch",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceVariant)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                    .then(
                        if (onRegionClick != null) {
                            Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClick = onRegionClick
                                )
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Change streaming region, currently $regionLabel"
                                }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Region",
                        tint = CinematicAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = regionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ContentHighEmphasis,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CinematicCrimson,
                        strokeWidth = 2.dp
                    )
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ContentLowEmphasis,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(color = ContentMediumEmphasis)
                            )
                        }
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ContentHighEmphasis
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CinematicAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Retry watch options",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }

            !hasProviders -> {
                // Graceful Empty Fallback UI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Not available for streaming in your region ($countryCode)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = ContentHighEmphasis
                                )
                            )
                        }
                        Text(
                            text = "Streaming rights and licensing change regularly. You can check TMDB or switch your region above.",
                            style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis)
                        )
                    }
                }
            }

            else -> {
                // Stream / Subscription
                if (flatrate.isNotEmpty()) {
                    Text(
                        text = "Stream (Included with Subscription)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = CinematicAmber,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WatchProviderRow(
                        providers = flatrate,
                        onProviderClick = onProviderClick
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Rent
                if (rent.isNotEmpty()) {
                    Text(
                        text = "Rent (Digital HD/4K)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ContentHighEmphasis,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WatchProviderRow(
                        providers = rent,
                        onProviderClick = onProviderClick
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Buy
                if (buy.isNotEmpty()) {
                    Text(
                        text = "Buy (Own Forever)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ContentHighEmphasis,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WatchProviderRow(
                        providers = buy,
                        onProviderClick = onProviderClick
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // JustWatch & TMDB External Watch Link
        if (!watchLink.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenWatchLink(watchLink) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ContentHighEmphasis
                )
            ) {
                Text(
                    text = "View options on JustWatch / TMDB",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = CinematicAmber
                )
            }
        }

        // External provider reference disclosure
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Streaming availability is provided for informational reference via TMDB/JustWatch. Provider badges reflect external subscription and rental options rather than direct in-app playback.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = ContentLowEmphasis,
                fontSize = 11.sp,
                lineHeight = 15.sp
            ),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
