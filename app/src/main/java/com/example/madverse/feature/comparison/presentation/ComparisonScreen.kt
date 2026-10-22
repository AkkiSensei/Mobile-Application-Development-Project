package com.example.madverse.feature.comparison.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.MoviePosterCard
import com.example.madverse.core.designsystem.components.SectionEmptyCard
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.components.TmdbAttributionFooter
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ErrorRed
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ComparisonScreen(
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Movie Comparison",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Compare up to three movies side by side across ratings, runtime, box office, and genres. Swipe horizontally to view all columns.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = ContentMediumEmphasis,
                    lineHeight = 20.sp
                )
            )
        }

        if (state.entries.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Movie Header Cards Row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.entries.forEachIndexed { index, entry ->
                            key(entry.id) {
                                Box(
                                    modifier = Modifier
                                        .width(168.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianSurface)
                                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Column Tag
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CinematicAmber.copy(alpha = 0.2f))
                                                .border(0.5.dp, CinematicAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Movie ${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = CinematicAmber,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        val movie = remember(entry.movie, entry.details) {
                                            entry.details?.toMovie() ?: entry.movie
                                        }

                                        if (movie != null) {
                                            MoviePosterCard(
                                                movie = movie,
                                                onClick = { onMovieClick(entry.id) },
                                                cardWidth = 152.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .width(152.dp)
                                                    .height(200.dp)
                                                    .background(ObsidianSurfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Movie ${entry.id}",
                                                    color = ContentMediumEmphasis
                                                )
                                            }
                                        }

                                        // Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.replace(entry.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = ContentHighEmphasis
                                                ),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = CinematicAmber
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Replace", fontSize = 12.sp)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            OutlinedButton(
                                                onClick = { viewModel.remove(entry.id) },
                                                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = ErrorRed
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove movie ${index + 1}",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = ErrorRed
                                                )
                                            }
                                        }

                                        if (entry.loading) {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = CinematicCrimson
                                            )
                                        }

                                        if (entry.error != null) {
                                            Text(
                                                text = entry.error,
                                                color = ErrorRed,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            OutlinedButton(
                                                onClick = { viewModel.loadDetails(entry.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Retry details", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Comparison Table Rows
                    ComparisonRow("Release Date", state.entries) {
                        it.details?.releaseDate ?: it.movie?.releaseDate
                    }
                    ComparisonRow("TMDB Audience Rating", state.entries) { entry ->
                        val votes = entry.details?.voteCount ?: entry.movie?.voteCount ?: 0
                        if (votes > 0) "${entry.details?.formattedRating ?: entry.movie?.formattedRating} / 10" else null
                    }
                    ComparisonRow("Vote Count", state.entries) {
                        (it.details?.voteCount ?: it.movie?.voteCount)?.toString()
                    }
                    ComparisonRow("Genres", state.entries) {
                        it.details?.genres?.map { genre -> genre.name }?.joinToString(", ")?.takeIf(String::isNotBlank)
                    }
                    ComparisonRow("Runtime", state.entries) {
                        (it.details?.runtimeMinutes ?: it.movie?.runtimeMinutes)?.takeIf { minutes -> minutes > 0 }?.let { minutes -> "$minutes min" }
                    }
                    ComparisonRow("Original Language", state.entries) {
                        it.details?.originalLanguage?.uppercase() ?: it.movie?.originalLanguage?.uppercase()
                    }
                    ComparisonRow("Popularity Score", state.entries) {
                        it.movie?.popularity?.toString()
                    }
                    ComparisonRow("Overview", state.entries) {
                        it.details?.overview ?: it.movie?.overview
                    }
                    ComparisonRow("Budget (USD)", state.entries) {
                        it.details?.budget?.takeIf { value -> value > 0 }?.let { formatCurrency(it) }
                    }
                    ComparisonRow("Revenue (USD)", state.entries) {
                        it.details?.revenue?.takeIf { value -> value > 0 }?.let { formatCurrency(it) }
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = CinematicAmber,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Movies Selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = ContentHighEmphasis
                            )
                        )
                        Text(
                            text = "Search for a movie below or select 'Compare this movie' from Recommendations to begin side-by-side comparison.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ContentMediumEmphasis,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }

        // Selection & Search Section
        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Movies to Compare",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (state.entries.size == 3) CinematicCrimson.copy(alpha = 0.2f) else ObsidianSurfaceVariant)
                        .border(0.5.dp, if (state.entries.size == 3) CinematicCrimson else ObsidianCardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${state.entries.size}/3 selected",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (state.entries.size == 3) CinematicCrimson else ContentMediumEmphasis,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (state.replacingId != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CinematicAmber.copy(alpha = 0.15f))
                        .border(1.dp, CinematicAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Replacing Movie ${state.entries.indexOfFirst { it.id == state.replacingId } + 1}. Select a result below.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = ContentHighEmphasis
                                )
                            )
                        }
                        TextButton(
                            onClick = { viewModel.replace(null) },
                            colors = ButtonDefaults.textButtonColors(contentColor = CinematicAmber)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = { Text("Search movies by title to compare...", color = ContentLowEmphasis) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ContentMediumEmphasis,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianSurfaceVariant,
                    unfocusedContainerColor = ObsidianSurfaceVariant,
                    focusedBorderColor = CinematicCrimson,
                    unfocusedBorderColor = ObsidianCardBorder,
                    focusedTextColor = ContentHighEmphasis,
                    unfocusedTextColor = ContentHighEmphasis,
                    cursorColor = CinematicCrimson
                )
            )

            state.selectionError?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (state.searching) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinematicCrimson
                )
            }
        }

        state.searchError?.let { message ->
            item {
                SectionErrorCard(
                    errorMessage = message,
                    onRetry = { viewModel.search(state.query) }
                )
            }
        }

        if (state.query.isNotBlank() && !state.searching && state.searchError == null && state.results.isEmpty()) {
            item {
                SectionEmptyCard(message = "No movies found. Try another title.")
            }
        }

        items(state.results, key = { it.id }) { movie ->
            val selected = state.entries.any { it.id == movie.id }
            val canSelect = !selected && (state.entries.size < 3 || state.replacingId != null)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MovieHorizontalCard(movie = movie, onClick = { viewModel.select(movie) })

                    Button(
                        onClick = { viewModel.select(movie) },
                        enabled = canSelect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.replacingId != null) CinematicAmber else CinematicCrimson,
                            contentColor = if (state.replacingId != null) ObsidianBackground else ContentHighEmphasis,
                            disabledContainerColor = ObsidianSurfaceVariant,
                            disabledContentColor = ContentLowEmphasis
                        )
                    ) {
                        when {
                            selected -> {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Already Selected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            state.replacingId != null -> {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use as replacement", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            else -> {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add as Movie ${state.entries.size + 1}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            TmdbAttributionFooter()
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    entries: List<ComparisonEntry>,
    value: (ComparisonEntry) -> String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.forEach { entry ->
                Column(modifier = Modifier.width(168.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CinematicAmber,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val textVal = value(entry)?.takeIf(String::isNotBlank)
                    if (textVal != null) {
                        Text(
                            text = textVal,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = ContentHighEmphasis,
                                fontSize = 13.sp
                            )
                        )
                    } else {
                        Text(
                            text = "Not available",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                color = ContentLowEmphasis,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

internal fun formatCurrency(amount: Long): String {
    return try {
        "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount)
    } catch (_: Exception) {
        "$$amount"
    }
}
