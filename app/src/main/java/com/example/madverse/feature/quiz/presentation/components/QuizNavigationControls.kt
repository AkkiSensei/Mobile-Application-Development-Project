package com.example.madverse.feature.quiz.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.madverse.feature.quiz.presentation.QuestionStep

/**
 * Top progress header showing linear step progress and current step label.
 */
@Composable
fun QuizProgressBar(
    currentStep: QuestionStep,
    progress: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Step ${currentStep.stepNumber} of ${QuestionStep.entries.size}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CinematicAmber,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = currentStep.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ContentHighEmphasis,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceVariant)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = "Restart Quiz",
                    tint = ContentMediumEmphasis,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = CinematicCrimson,
            trackColor = ObsidianSurfaceVariant
        )
    }
}

/**
 * Bottom action bar with Back and Next/Complete buttons.
 */
@Composable
fun QuizActionBar(
    canGoBack: Boolean,
    isLastStep: Boolean,
    isNextEnabled: Boolean,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            OutlinedButton(
                onClick = onBackClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.35f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back")
            }
        }

        Button(
            onClick = onNextClick,
            enabled = isNextEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CinematicCrimson,
                contentColor = ContentHighEmphasis,
                disabledContainerColor = ObsidianSurfaceVariant,
                disabledContentColor = ContentLowEmphasis
            ),
            modifier = Modifier
                .weight(if (canGoBack) 0.65f else 1f)
                .height(48.dp)
        ) {
            if (isLastStep) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find My Matches", fontWeight = FontWeight.Bold)
            } else {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
