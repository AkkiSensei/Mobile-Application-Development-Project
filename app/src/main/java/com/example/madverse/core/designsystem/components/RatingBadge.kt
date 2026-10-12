package com.example.madverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.RatingGold
import java.util.Locale

/**
 * Visual presentation style for the movie rating badge.
 */
enum class RatingBadgeStyle {
    /**
     * Overlay pill badge with obsidian semi-transparent background and subtle border.
     * Ideal for placing in the corner of movie poster cards.
     */
    OverlayBadge,

    /**
     * Compact horizontal row with star icon and score.
     * Ideal for movie metadata rows and list items.
     */
    Inline,

    /**
     * Prominent high-contrast rating display with "/ 10" scale denominator.
     * Ideal for movie details hero headers.
     */
    Prominent
}

/**
 * Standardized movie rating badge component for the MADVerse design system.
 *
 * Supports three visual styles:
 * - [RatingBadgeStyle.OverlayBadge]: Overlay pill badge with translucent obsidian background.
 * - [RatingBadgeStyle.Inline]: Compact inline row with star and numeric score.
 * - [RatingBadgeStyle.Prominent]: Header-grade rating display with "/ 10" scale.
 *
 * Automatically provides accessible screen reader descriptions (e.g. "Rated 8.4 out of 10 stars")
 * and formats vote scores to one decimal place.
 */
@Composable
fun MovieRatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
    style: RatingBadgeStyle = RatingBadgeStyle.OverlayBadge,
    starSize: Dp = when (style) {
        RatingBadgeStyle.OverlayBadge -> 12.dp
        RatingBadgeStyle.Inline -> 13.dp
        RatingBadgeStyle.Prominent -> 18.dp
    },
    textColor: Color = ContentHighEmphasis,
    starColor: Color = RatingGold,
    showOutOfTen: Boolean = style == RatingBadgeStyle.Prominent
) {
    if (rating <= 0.0) return

    val formattedRating = String.format(Locale.US, "%.1f", rating)
    val accessibleText = "Rated $formattedRating out of 10 stars"

    when (style) {
        RatingBadgeStyle.OverlayBadge -> {
            Box(
                modifier = modifier
                    .semantics { contentDescription = accessibleText }
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianBackground.copy(alpha = 0.85f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = starColor,
                        modifier = Modifier.size(starSize)
                    )
                    Text(
                        text = formattedRating,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        RatingBadgeStyle.Inline -> {
            Row(
                modifier = modifier.semantics { contentDescription = accessibleText },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = starColor,
                    modifier = Modifier.size(starSize)
                )
                Text(
                    text = formattedRating,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
            }
        }

        RatingBadgeStyle.Prominent -> {
            Row(
                modifier = modifier.semantics { contentDescription = accessibleText },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = starColor,
                    modifier = Modifier.size(starSize)
                )
                Text(
                    text = if (showOutOfTen) "$formattedRating / 10" else formattedRating,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
            }
        }
    }
}
