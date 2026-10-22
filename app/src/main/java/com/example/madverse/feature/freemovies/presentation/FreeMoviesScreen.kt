package com.example.madverse.feature.freemovies.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.SectionEmptyCard
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.Movie

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreeMoviesScreen(
    onPlay: (String) -> Unit,
    viewModel: FreeMoviesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun openEvidence(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "No browser could open this source.", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Free Movies Catalogue",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ContentHighEmphasis
                                )
                            )
                        }
                        Text(
                            text = "Complete films with verified publisher and license details. Regional and embedding availability are determined by YouTube and rights holders.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ContentMediumEmphasis
                            )
                        )
                    }
                }
            }

            when (val result = state) {
                UiState.Loading -> item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CinematicAmber,
                        trackColor = ObsidianSurfaceVariant
                    )
                }
                is UiState.Empty -> item {
                    SectionEmptyCard(message = result.message)
                }
                is UiState.Error -> item {
                    SectionErrorCard(
                        errorMessage = result.message,
                        onRetry = viewModel::load
                    )
                }
                is UiState.Success -> items(
                    items = result.data,
                    key = { it.catalogueId }
                ) { film ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Format & verification header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CinematicCrimson.copy(alpha = 0.2f))
                                        .border(BorderStroke(1.dp, CinematicCrimson.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = film.format.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = CinematicAmber
                                        )
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = CinematicAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Verified ${film.verifiedOn}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ContentMediumEmphasis
                                        )
                                    )
                                }
                            }

                            // Shared presentation card (catalogue IDs, never TMDB movie IDs)
                            MovieHorizontalCard(
                                movie = Movie(
                                    id = 0,
                                    title = film.title,
                                    overview = film.description,
                                    posterUrl = film.thumbnailUrl,
                                    backdropUrl = null,
                                    releaseDate = null,
                                    voteAverage = 0.0,
                                    voteCount = 0,
                                    genreIds = emptyList(),
                                    popularity = 0.0
                                ),
                                onClick = { onPlay(film.catalogueId) }
                            )

                            // Publisher and attribution info
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = film.publisher,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = ContentHighEmphasis
                                    )
                                )
                                Text(
                                    text = film.attribution,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ContentMediumEmphasis
                                    )
                                )
                                if (film.regionNotes.isNotBlank()) {
                                    Text(
                                        text = film.regionNotes,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ContentMediumEmphasis
                                        )
                                    )
                                }
                            }

                            // Watch action button
                            Button(
                                onClick = { onPlay(film.catalogueId) },
                                enabled = film.isEligibleForYouTube,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinematicCrimson,
                                    contentColor = ContentHighEmphasis
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (film.isEligibleForYouTube) "Watch in App" else "Playback Unavailable",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!film.isEligibleForYouTube) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CinematicAmber.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, CinematicAmber.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
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
                                        text = "In-app playback unavailable: source or rights authorization is not verified for direct streaming.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = ContentHighEmphasis)
                                    )
                                }
                            }

                            // Evidence and rights links
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Rights & Verification Sources",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ContentMediumEmphasis,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { openEvidence(film.sourceUrl) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                        border = BorderStroke(1.dp, ObsidianCardBorder)
                                    ) {
                                        Text(
                                            text = "Publisher Page",
                                            style = MaterialTheme.typography.labelSmall.copy(color = CinematicAmber)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = CinematicAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { openEvidence(film.rightsUrl) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                        border = BorderStroke(1.dp, ObsidianCardBorder)
                                    ) {
                                        Text(
                                            text = "Rights Evidence",
                                            style = MaterialTheme.typography.labelSmall.copy(color = ContentMediumEmphasis)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = ContentMediumEmphasis,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { openEvidence(film.licenseUrl) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                        border = BorderStroke(1.dp, ObsidianCardBorder)
                                    ) {
                                        Text(
                                            text = "License Terms",
                                            style = MaterialTheme.typography.labelSmall.copy(color = ContentMediumEmphasis)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = ContentMediumEmphasis,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Catalogue attribution belongs to each film's publisher. Catalogue films are curated separately from TMDB trailers.",
                    style = MaterialTheme.typography.bodySmall.copy(color = ContentMediumEmphasis),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
