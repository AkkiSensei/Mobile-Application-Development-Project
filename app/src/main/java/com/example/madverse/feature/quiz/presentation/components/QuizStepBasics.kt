package com.example.madverse.feature.quiz.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.feature.quiz.presentation.QuizGenreItem
import com.example.madverse.feature.quiz.presentation.QuizLanguageOption

/**
 * Step 1: Interactive Genre Selection composable (multi-select).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionStep(
    selectedGenreIds: Set<Int>,
    onGenreToggled: (Int) -> Unit,
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
                title = "What genres do you love?",
                subtitle = "Pick one or more. We will heavily weight these in your recommendation results.",
                badgeText = if (selectedGenreIds.isEmpty()) "Select at least 1" else "${selectedGenreIds.size} Selected",
                isBadgeActive = selectedGenreIds.isNotEmpty()
            )
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuizGenreItem.CANONICAL_GENRES.forEach { genre ->
                    val isSelected = selectedGenreIds.contains(genre.id)
                    GenreChipCard(
                        genre = genre,
                        isSelected = isSelected,
                        onClick = { onGenreToggled(genre.id) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Step 2: Preferred Audio Language Selection composable (single-select).
 */
@Composable
fun LanguageSelectionStep(
    selectedLanguageCode: String?,
    onLanguageSelected: (String?) -> Unit,
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
                title = "Preferred Audio Language",
                subtitle = "Choose your preferred original language or explore global cinema.",
                badgeText = if (selectedLanguageCode == null) "All Languages" else selectedLanguageCode.uppercase(),
                isBadgeActive = true
            )
        }

        items(QuizLanguageOption.ALL_LANGUAGES) { language ->
            val isSelected = selectedLanguageCode == language.code
            LanguageOptionCard(
                language = language,
                isSelected = isSelected,
                onClick = { onLanguageSelected(language.code) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Helper Presentation Sub-Composables
// =============================================================================

@Composable
fun StepHeaderBanner(
    title: String,
    subtitle: String,
    badgeText: String,
    isBadgeActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                ),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isBadgeActive) CinematicCrimson.copy(alpha = 0.2f)
                        else ObsidianSurfaceVariant
                    )
                    .border(
                        1.dp,
                        if (isBadgeActive) CinematicCrimson else ObsidianCardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isBadgeActive) CinematicCrimson else ContentMediumEmphasis,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ContentMediumEmphasis,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun GenreChipCard(
    genre: QuizGenreItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .semantics {
                role = Role.Checkbox
                selected = isSelected
            }
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) CinematicCrimson.copy(alpha = 0.22f)
                else ObsidianSurface
            )
            .border(
                1.dp,
                if (isSelected) CinematicCrimson else ObsidianCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(
                role = Role.Checkbox,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(CinematicCrimson),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ContentHighEmphasis,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = ContentLowEmphasis,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) ContentHighEmphasis else ContentMediumEmphasis
                    )
                )
                Text(
                    text = genre.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelected) CinematicAmber else ContentLowEmphasis,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    language: QuizLanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) CinematicAmber.copy(alpha = 0.15f)
                else ObsidianSurface
            )
            .border(
                1.dp,
                if (isSelected) CinematicAmber else ObsidianCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) CinematicAmber.copy(alpha = 0.25f)
                        else ObsidianSurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = if (isSelected) CinematicAmber else ContentMediumEmphasis,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) ContentHighEmphasis else ContentMediumEmphasis
                    )
                )
                Text(
                    text = language.nativeName,
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
                    if (isSelected) CinematicAmber
                    else ObsidianSurfaceVariant
                )
                .border(
                    1.dp,
                    if (isSelected) CinematicAmber else ObsidianCardBorder,
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
