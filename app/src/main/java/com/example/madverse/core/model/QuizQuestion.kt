package com.example.madverse.core.model

/**
 * Quiz question model for the taste discovery flow.
 */
data class QuizQuestion(
    val id: String,
    val title: String,
    val description: String,
    val options: List<QuizOption>,
    val allowsMultipleSelection: Boolean = false
)

data class QuizOption(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val genreIds: List<Int> = emptyList(),
    val mood: MovieMood? = null,
    val era: MovieEra? = null,
    val runtime: RuntimePreference? = null
)
