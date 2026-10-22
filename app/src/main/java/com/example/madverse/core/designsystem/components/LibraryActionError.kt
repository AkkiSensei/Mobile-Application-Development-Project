package com.example.madverse.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LibraryActionError(message: String?, onDismiss: () -> Unit) {
    if (message != null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library update failed") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
