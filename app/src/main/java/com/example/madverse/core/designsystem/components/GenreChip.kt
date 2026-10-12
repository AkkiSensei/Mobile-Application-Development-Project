package com.example.madverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant

/**
 * Standardized read-only or clickable genre pill chip.
 *
 * Used across Movie Details, Movie Cards, Search results, and Catalog views.
 */
@Composable
fun GenreChip(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    textColor: Color = CinematicAmber,
    backgroundColor: Color = ObsidianSurface,
    borderColor: Color = ObsidianCardBorder,
    cornerRadius: Dp = 16.dp
) {
    val chipModifier = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(backgroundColor)
        .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
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
        )
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Box(
        modifier = chipModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        )
    }
}

/**
 * Interactive, selectable genre chip designed for search filters, genre exploration,
 * and category preferences.
 *
 * Enforces Android touch target compliance (>= 48dp) and accessibility semantics.
 */
@Composable
fun SelectableGenreChip(
    name: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = CinematicCrimson,
    cornerRadius: Dp = 12.dp
) {
    val backgroundColor = if (isSelected) activeColor.copy(alpha = 0.2f) else ObsidianSurface
    val borderColor = if (isSelected) activeColor else ObsidianCardBorder
    val textColor = if (isSelected) ContentHighEmphasis else ContentMediumEmphasis

    Box(
        modifier = modifier
            .semantics {
                role = Role.Checkbox
                selected = isSelected
            }
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .clickable(
                role = Role.Checkbox,
                onClick = { onSelectedChange(!isSelected) }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Responsive FlowRow displaying a collection of genre names with standardized spacing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChipFlowRow(
    genres: List<String>,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    onGenreClick: ((String) -> Unit)? = null
) {
    if (genres.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        genres.forEach { genreName ->
            GenreChip(
                name = genreName,
                onClick = onGenreClick?.let { { it(genreName) } }
            )
        }
    }
}
