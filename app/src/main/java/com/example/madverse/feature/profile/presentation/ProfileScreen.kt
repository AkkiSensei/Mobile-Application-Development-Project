package com.example.madverse.feature.profile.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.CinematicViolet
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ErrorRed
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.designsystem.theme.SuccessGreen
import com.example.madverse.core.model.SyncState
import com.example.madverse.feature.quiz.presentation.QuizGenreItem

private fun getGenreNameById(id: Int): String =
    QuizGenreItem.CANONICAL_GENRES.find { it.id == id }?.name ?: "Genre $id"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Sign out confirmation dialog
    if (uiState.showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSignOutDialog(false) },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out? Your cloud data will remain safely synchronized on Firebase, and you will return to guest mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ContentMediumEmphasis
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.signOut(onSignedOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicCrimson)
                ) {
                    Text("Sign Out", color = ContentHighEmphasis)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSignOutDialog(false) }) {
                    Text("Cancel", color = ContentMediumEmphasis)
                }
            }
        )
    }

    // Account deletion confirmation dialog
    if (uiState.showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteAccountDialog(false) },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Delete Account Permanently?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
            },
            text = {
                Text(
                    text = "Warning: This action is irreversible. All your cloud favorites, watchlist, quiz preferences, and Firebase profile data will be permanently wiped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ContentMediumEmphasis
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(onSignedOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Everything", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteAccountDialog(false) }) {
                    Text("Cancel", color = ContentMediumEmphasis)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Banner Messages
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.dismissError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = ErrorRed)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.infoMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.infoMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.dismissInfo() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = SuccessGreen)
                    }
                }
            }
        }

        // Avatar and Profile Identity Header
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(CinematicCrimson, CinematicAmber)
                    )
                )
                .border(3.dp, ObsidianCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.authUser?.photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uiState.authUser?.photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = if (uiState.isGuest) "MV" else uiState.authUser?.initials ?: "MV",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display Name & Edit
        if (uiState.isGuest) {
            Text(
                text = "Guest Explorer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            )
            Text(
                text = "Offline Mode — Local device storage active",
                style = MaterialTheme.typography.bodySmall,
                color = ContentMediumEmphasis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNavigateToAuth,
                colors = ButtonDefaults.buttonColors(containerColor = CinematicCrimson),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign In / Create Account", fontWeight = FontWeight.Bold)
            }
        } else {
            if (uiState.isEditingName) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.editedName,
                        onValueChange = viewModel::onEditedNameChanged,
                        singleLine = true,
                        label = { Text("Display Name") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )
                    IconButton(onClick = viewModel::saveDisplayName) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = SuccessGreen)
                    }
                    IconButton(onClick = viewModel::cancelEditingName) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ContentLowEmphasis)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.authUser?.displayName?.ifBlank { "Movie Buff" } ?: "Movie Buff",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis
                    )
                    IconButton(
                        onClick = viewModel::startEditingName,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit name",
                            tint = ContentMediumEmphasis,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = uiState.authUser?.email ?: "No email associated",
                style = MaterialTheme.typography.bodyMedium,
                color = ContentMediumEmphasis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Verification & Provider Badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.authUser?.isEmailVerified == true) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (uiState.authUser?.providerId != "google.com") {
                    Surface(
                        color = CinematicAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { viewModel.sendEmailVerification() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Unverified (Tap to verify)",
                                style = MaterialTheme.typography.labelSmall,
                                color = CinematicAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (uiState.authUser?.providerId == "google.com") "Google Account" else "Email Auth",
                        style = MaterialTheme.typography.labelSmall,
                        color = ContentMediumEmphasis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Library Counters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorites Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onNavigateToFavorites() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = CinematicCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.favoritesCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ContentHighEmphasis
                        )
                    }
                    Text(
                        text = "Favorite Movies",
                        style = MaterialTheme.typography.bodySmall,
                        color = ContentMediumEmphasis
                    )
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(ObsidianCardBorder)
                )

                // Watchlist Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onNavigateToWatchlist() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = CinematicAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.watchlistCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ContentHighEmphasis
                        )
                    }
                    Text(
                        text = "In Watchlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = ContentMediumEmphasis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloud Synchronization Status Card (if authenticated)
        if (!uiState.isGuest) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (uiState.syncState) {
                        is SyncState.Synced -> {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Synchronized",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ContentHighEmphasis
                                )
                                Text(
                                    text = "Favorites, watchlist & quiz profile backed up in Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ContentMediumEmphasis
                                )
                            }
                        }
                        is SyncState.Syncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = CinematicAmber,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Synchronizing...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ContentHighEmphasis
                                )
                                Text(
                                    text = "Transmitting updates to Cloud Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ContentMediumEmphasis
                                )
                            }
                        }
                        is SyncState.OfflinePending -> {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offline Changes Pending",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CinematicAmber
                                )
                                Text(
                                    text = "Will automatically upload once connectivity is restored",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ContentMediumEmphasis
                                )
                            }
                            IconButton(onClick = viewModel::triggerManualSync) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry Sync", tint = CinematicAmber)
                            }
                        }
                        is SyncState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync Encountered an Issue",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                                Text(
                                    text = (uiState.syncState as SyncState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ContentMediumEmphasis
                                )
                            }
                            IconButton(onClick = viewModel::triggerManualSync) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry Sync", tint = ContentHighEmphasis)
                            }
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = ContentMediumEmphasis,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Sync Ready",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ContentHighEmphasis
                                )
                                Text(
                                    text = "Active session linked to Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ContentMediumEmphasis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Movie Taste & Quiz Preferences Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CinematicAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Movie Taste Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ContentHighEmphasis
                        )
                    }
                    TextButton(onClick = onRetakeQuiz) {
                        Text(
                            text = if (uiState.hasSavedPreferences) "Retake Quiz" else "Take Quiz",
                            color = CinematicCrimson,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Detail Rows
                PreferenceRow(label = "Current Vibe", value = uiState.preferences.mood.displayName)
                PreferenceRow(label = "Preferred Era", value = uiState.preferences.era.displayName)
                PreferenceRow(label = "Runtime", value = uiState.preferences.runtimePreference.displayName)
                PreferenceRow(
                    label = "Language",
                    value = uiState.preferences.preferredLanguage?.let { "$it (Preferred)" } ?: "Any Language"
                )

                if (uiState.preferences.selectedGenreIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Preferred Genres:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ContentMediumEmphasis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.preferences.selectedGenreIds.forEach { genreId ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = getGenreNameById(genreId),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ContentHighEmphasis
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = ObsidianSurfaceVariant
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Actions (Sign Out & Delete)
        if (!uiState.isGuest) {
            OutlinedButton(
                onClick = { viewModel.showSignOutDialog(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ContentHighEmphasis),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of Account")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { viewModel.showDeleteAccountDialog(true) },
                colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Account & Cloud Data")
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ContentMediumEmphasis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = ContentHighEmphasis
        )
    }
}
