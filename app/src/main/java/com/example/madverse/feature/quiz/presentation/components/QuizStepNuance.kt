package com.example.madverse.feature.quiz.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.CinematicViolet
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.designsystem.theme.RatingGold
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference

/**
 * Step 3: Movie Mood / Viewing Atmosphere Selection (single-select).
 */
@Composable
fun MoodSelectionStep(
    selectedMood: MovieMood,
    onMoodSelected: (MovieMood) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StepHeaderBanner(
                title = "What's your viewing mood?",
                subtitle = "Pick the emotion or feeling you're chasing tonight.",
                badgeText = selectedMood.name.replace("_", " "),
                isBadgeActive = true
            )
        }

        items(MovieMood.entries) { mood ->
            val isSelected = selectedMood == mood
            NuanceOptionCard(
                title = mood.displayName,
                subtitle = moodToDescription(mood),
                icon = Icons.Filled.Mood,
                iconTint = CinematicViolet,
                isSelected = isSelected,
                onClick = { onMoodSelected(mood) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Step 4: Cinematic Era Selection (single-select).
 */
@Composable
fun EraSelectionStep(
    selectedEra: MovieEra,
    onEraSelected: (MovieEra) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StepHeaderBanner(
                title = "Which era of cinema?",
                subtitle = "Select your preferred period of filmmaking.",
                badgeText = selectedEra.name.replace("_", " "),
                isBadgeActive = true
            )
        }

        items(MovieEra.entries) { era ->
            val isSelected = selectedEra == era
            NuanceOptionCard(
                title = era.displayName,
                subtitle = eraToDescription(era),
                icon = Icons.Filled.History,
                iconTint = CinematicAmber,
                isSelected = isSelected,
                onClick = { onEraSelected(era) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Step 5: Runtime Preference Selection (single-select).
 */
@Composable
fun RuntimeSelectionStep(
    selectedRuntime: RuntimePreference,
    onRuntimeSelected: (RuntimePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StepHeaderBanner(
                title = "How long do you want to watch?",
                subtitle = "Pick your ideal duration range for tonight.",
                badgeText = selectedRuntime.name.replace("_", " "),
                isBadgeActive = true
            )
        }

        items(RuntimePreference.entries) { runtime ->
            val isSelected = selectedRuntime == runtime
            NuanceOptionCard(
                title = runtime.displayName,
                subtitle = runtimeToDescription(runtime),
                icon = Icons.Filled.Schedule,
                iconTint = CinematicCrimson,
                isSelected = isSelected,
                onClick = { onRuntimeSelected(runtime) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Step 6: Minimum Rating Quality Baseline Selection (interactive slider).
 */
@Composable
fun RatingSelectionStep(
    minRatingThreshold: Double,
    onRatingChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StepHeaderBanner(
                title = "Quality Baseline",
                subtitle = "Set your minimum acceptable score. Candidates below this threshold will be filtered out.",
                badgeText = String.format("%.1f★+", minRatingThreshold),
                isBadgeActive = true
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = RatingGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = String.format("%.1f", minRatingThreshold),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ContentHighEmphasis
                        )
                    )
                    Text(
                        text = "out of 10",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ContentMediumEmphasis
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = ratingToLabel(minRatingThreshold),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = CinematicAmber
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Slider(
                    value = minRatingThreshold.toFloat(),
                    onValueChange = { onRatingChanged(Math.round(it * 2.0) / 2.0) }, // 0.5 increments
                    valueRange = 5.0f..8.5f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = CinematicCrimson,
                        activeTrackColor = CinematicCrimson,
                        inactiveTrackColor = ObsidianSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5.0★ (Lenient)", style = MaterialTheme.typography.labelSmall.copy(color = ContentLowEmphasis))
                    Text("7.0★ (Balanced)", style = MaterialTheme.typography.labelSmall.copy(color = ContentLowEmphasis))
                    Text("8.5★ (Strict)", style = MaterialTheme.typography.labelSmall.copy(color = ContentLowEmphasis))
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CinematicViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Our recommendation engine uses this baseline to reward high-rated films and penalize lower quality movies.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ContentMediumEmphasis,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Helper Presentation Sub-Composables & Descriptors
// =============================================================================

@Composable
private fun NuanceOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) iconTint.copy(alpha = 0.16f)
                else ObsidianSurface
            )
            .border(
                1.dp,
                if (isSelected) iconTint else ObsidianCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) iconTint.copy(alpha = 0.25f)
                        else ObsidianSurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) iconTint else ContentMediumEmphasis,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) ContentHighEmphasis else ContentMediumEmphasis
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentLowEmphasis
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) iconTint
                    else ObsidianSurfaceVariant
                )
                .border(
                    1.dp,
                    if (isSelected) iconTint else ObsidianCardBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ObsidianSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun moodToDescription(mood: MovieMood): String = when (mood) {
    MovieMood.ANY -> "Open to any emotion, surprise me"
    MovieMood.MIND_BENDING -> "Complex plots, paradoxes & cerebral twists"
    MovieMood.ADRENALINE -> "Pulse-pounding action, pursuits & heroism"
    MovieMood.FEEL_GOOD -> "Warmth, heartwarming humor & wholesome laughs"
    MovieMood.DARK_AND_SPOOKY -> "Eerie atmosphere, shocks & psychological dread"
    MovieMood.EMOTIONAL -> "Bittersweet human drama, romance & raw passion"
}

private fun eraToDescription(era: MovieEra): String = when (era) {
    MovieEra.ANY -> "All eras from silent pioneers to today"
    MovieEra.CLASSIC -> "Timeless storytelling before 1990"
    MovieEra.MODERN -> "Blockbusters & cinematic evolution (1990 - 2014)"
    MovieEra.CONTEMPORARY -> "Modern filmmaking craft (2015 to present)"
}

private fun runtimeToDescription(runtime: RuntimePreference): String = when (runtime) {
    RuntimePreference.ANY -> "Whatever time the story requires"
    RuntimePreference.QUICK -> "Under 100 minutes — fast-paced & concise"
    RuntimePreference.STANDARD -> "100 to 140 minutes — traditional length"
    RuntimePreference.EPIC -> "Over 140 minutes — expansive worldbuilding"
}

private fun ratingToLabel(rating: Double): String = when {
    rating >= 8.0 -> "Masterpieces & Critical Darlings"
    rating >= 7.0 -> "Well-Reviewed & High Quality"
    rating >= 6.0 -> "Popular & Accessible Cinema"
    else -> "Inclusive Baseline (All Candidates)"
}
