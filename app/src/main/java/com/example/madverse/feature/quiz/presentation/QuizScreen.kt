package com.example.madverse.feature.quiz.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.madverse.feature.quiz.presentation.components.EraSelectionStep
import com.example.madverse.feature.quiz.presentation.components.GenreSelectionStep
import com.example.madverse.feature.quiz.presentation.components.LanguageSelectionStep
import com.example.madverse.feature.quiz.presentation.components.MoodSelectionStep
import com.example.madverse.feature.quiz.presentation.components.QuizActionBar
import com.example.madverse.feature.quiz.presentation.components.QuizProgressBar
import com.example.madverse.feature.quiz.presentation.components.RatingSelectionStep
import com.example.madverse.feature.quiz.presentation.components.RuntimeSelectionStep

/**
 * Standard alias for the Movie Taste Quiz screen used by navigation hosts.
 */
@Composable
fun QuizScreen(
    onQuizCompleted: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = hiltViewModel()
) {
    MoviePreferenceQuizScreen(
        onQuizCompleted = onQuizCompleted,
        modifier = modifier,
        viewModel = viewModel
    )
}

/**
 * Complete multi-step Movie Preference Quiz scaffold.
 * Backed by [QuizViewModel], managing state transitions, progress bars, and completion confirmation.
 */
@Composable
fun MoviePreferenceQuizScreen(
    onQuizCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        containerColor = ObsidianBackground,
        topBar = {
            QuizProgressBar(
                currentStep = uiState.currentStep,
                progress = uiState.progress,
                onReset = viewModel::resetQuiz
            )
        },
        bottomBar = {
            QuizActionBar(
                canGoBack = uiState.canGoBack,
                isLastStep = uiState.isLastStep,
                isNextEnabled = uiState.isCurrentStepValid && !uiState.isSaving,
                onBackClick = viewModel::goToPreviousStep,
                onNextClick = viewModel::goToNextStep
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    QuizLoadingView(modifier = Modifier.fillMaxSize())
                }

                uiState.errorMessage != null && !uiState.isSaving -> {
                    QuizErrorView(
                        errorMessage = uiState.errorMessage ?: "An unexpected error occurred.",
                        onRetry = viewModel::restoreExistingPreferences,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    AnimatedContent(
                        targetState = uiState.currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                            } else {
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                            }
                        },
                        label = "QuizStepTransition"
                    ) { step ->
                        when (step) {
                            QuestionStep.GENRES -> {
                                GenreSelectionStep(
                                    selectedGenreIds = uiState.answers.selectedGenreIds,
                                    onGenreToggled = viewModel::toggleGenre
                                )
                            }

                            QuestionStep.LANGUAGE -> {
                                LanguageSelectionStep(
                                    selectedLanguageCode = uiState.answers.preferredLanguage,
                                    onLanguageSelected = viewModel::setPreferredLanguage
                                )
                            }

                            QuestionStep.MOOD -> {
                                MoodSelectionStep(
                                    selectedMood = uiState.answers.mood,
                                    onMoodSelected = viewModel::setMood
                                )
                            }

                            QuestionStep.ERA -> {
                                EraSelectionStep(
                                    selectedEra = uiState.answers.era,
                                    onEraSelected = viewModel::setEra
                                )
                            }

                            QuestionStep.RUNTIME -> {
                                RuntimeSelectionStep(
                                    selectedRuntime = uiState.answers.runtimePreference,
                                    onRuntimeSelected = viewModel::setRuntimePreference
                                )
                            }

                            QuestionStep.RATING -> {
                                RatingSelectionStep(
                                    minRatingThreshold = uiState.answers.minRatingThreshold,
                                    onRatingChanged = viewModel::setMinRatingThreshold
                                )
                            }
                        }
                    }
                }
            }

            // Completion Confirmation Dialog
            if (uiState.showCompletionConfirmation) {
                QuizCompletionConfirmationDialog(
                    answers = uiState.answers,
                    isSaving = uiState.isSaving,
                    onConfirm = { viewModel.submitQuiz(onComplete = onQuizCompleted) },
                    onDismiss = viewModel::dismissCompletionDialog
                )
            }
        }
    }
}

// =============================================================================
// Helper Presentation Sub-Composables
// =============================================================================

@Composable
private fun QuizLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = CinematicCrimson,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Restoring taste preferences...",
                style = MaterialTheme.typography.bodyMedium.copy(color = ContentMediumEmphasis)
            )
        }
    }
}

@Composable
private fun QuizErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ContentMediumEmphasis,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CinematicCrimson)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun QuizCompletionConfirmationDialog(
    answers: QuizAnswers,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = ObsidianSurface,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CinematicCrimson.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = CinematicCrimson,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Save Taste Profile?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your answers will be saved to your local profile to power personalized movie recommendations.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = ContentMediumEmphasis,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Profile Summary Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceVariant)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryRow(label = "Genres", value = "${answers.selectedGenreIds.size} Selected")
                    SummaryRow(label = "Language", value = answers.preferredLanguage?.uppercase() ?: "All")
                    SummaryRow(label = "Mood", value = answers.mood.name.replace("_", " "))
                    SummaryRow(label = "Era", value = answers.era.name.replace("_", " "))
                    SummaryRow(label = "Runtime", value = answers.runtimePreference.name.replace("_", " "))
                    SummaryRow(label = "Min Rating", value = String.format("%.1f★+", answers.minRatingThreshold))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinematicCrimson,
                    contentColor = ContentHighEmphasis
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ContentHighEmphasis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Find Matches")
                }
            }
        },
        dismissButton = {
            if (!isSaving) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Review")
                }
            }
        }
    )
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = CinematicAmber
            )
        )
    }
}
