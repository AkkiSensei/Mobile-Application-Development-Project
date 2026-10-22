package com.example.madverse.feature.auth.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.R
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ErrorRed
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val webClientId = stringResource(R.string.default_web_client_id)

    // Guest data merge confirmation dialog
    if (uiState.showMergeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onMergeDismissed(onAuthSuccess) },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Import Guest Library?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                )
            },
            text = {
                Text(
                    text = "You have favorites and watchlist movies saved in your guest session. Would you like to merge them into your cloud account, or keep them separate?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ContentMediumEmphasis
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onMergeConfirmed(onAuthSuccess) },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicCrimson)
                ) {
                    Text("Merge into Account", color = ContentHighEmphasis)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.onMergeDismissed(onAuthSuccess) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ContentMediumEmphasis)
                ) {
                    Text("Keep Separate")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header / Brand Branding
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = CinematicCrimson,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "MADVerse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = ContentHighEmphasis
            )
            Text(
                text = when (uiState.mode) {
                    AuthMode.WELCOME -> "Your Personal Cinematic Universe"
                    AuthMode.SIGN_IN -> "Welcome Back"
                    AuthMode.SIGN_UP -> "Create Your MADVerse Account"
                    AuthMode.FORGOT_PASSWORD -> "Reset Your Password"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ContentMediumEmphasis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error & Info Feedback Banners
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
                    colors = CardDefaults.cardColors(containerColor = CinematicAmber.copy(alpha = 0.15f)),
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
                            color = CinematicAmber,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissInfo() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = CinematicAmber)
                        }
                    }
                }
            }

            // Mode-specific layouts
            when (uiState.mode) {
                AuthMode.WELCOME -> {
                    // Google Sign-In via Credential Manager
                    Button(
                        onClick = {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            coroutineScope.launch {
                                try {
                                    val result = credentialManager.getCredential(context = context, request = request)
                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                                    viewModel.signInWithGoogle(googleIdTokenCredential.idToken, onAuthSuccess)
                                } catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) {
                                    // User cancelled selection
                                } catch (e: Exception) {
                                    // Show user-friendly error
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianSurfaceVariant,
                            contentColor = ContentHighEmphasis
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !uiState.isGoogleSignInLoading
                    ) {
                        if (uiState.isGoogleSignInLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = CinematicCrimson,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = CinematicAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sign In with Email
                    Button(
                        onClick = { viewModel.setMode(AuthMode.SIGN_IN) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicCrimson,
                            contentColor = ContentHighEmphasis
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Email",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Create Account
                    OutlinedButton(
                        onClick = { viewModel.setMode(AuthMode.SIGN_UP) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ContentHighEmphasis)
                    ) {
                        Text(
                            text = "Create an Account",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Guest option
                    TextButton(
                        onClick = onContinueAsGuest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ContentMediumEmphasis
                        )
                    }
                    Text(
                        text = "Browse catalogue, take the taste quiz, and store favorites locally without creating an account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ContentMediumEmphasis.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                AuthMode.SIGN_IN -> {
                    // Back button to Welcome
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setMode(AuthMode.WELCOME) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CinematicCrimson
                            )
                        }
                        Text(
                            text = "Back to Options",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ContentMediumEmphasis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = viewModel::togglePasswordVisibility) {
                                Icon(
                                    imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.signInWithEmail(onAuthSuccess)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinematicAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.signInWithEmail(onAuthSuccess)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicCrimson,
                            contentColor = ContentHighEmphasis
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ContentHighEmphasis,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            style = MaterialTheme.typography.bodySmall,
                            color = ContentMediumEmphasis
                        )
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_UP) }) {
                            Text(
                                text = "Sign Up",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CinematicCrimson
                            )
                        }
                    }
                }

                AuthMode.SIGN_UP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setMode(AuthMode.WELCOME) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CinematicCrimson
                            )
                        }
                        Text(
                            text = "Back to Options",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ContentMediumEmphasis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.displayName,
                        onValueChange = viewModel::onDisplayNameChanged,
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Password (min 6 characters)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = viewModel::togglePasswordVisibility) {
                                Icon(
                                    imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChanged,
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.signUpWithEmail(onAuthSuccess)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.signUpWithEmail(onAuthSuccess)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicCrimson,
                            contentColor = ContentHighEmphasis
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ContentHighEmphasis,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Create Account",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Already have an account? ",
                            style = MaterialTheme.typography.bodySmall,
                            color = ContentMediumEmphasis
                        )
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CinematicCrimson
                            )
                        }
                    }
                }

                AuthMode.FORGOT_PASSWORD -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CinematicCrimson
                            )
                        }
                        Text(
                            text = "Back to Sign In",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ContentMediumEmphasis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Enter your registered email address and we will send you a password reset link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ContentMediumEmphasis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.sendPasswordResetEmail()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinematicCrimson,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = ContentHighEmphasis,
                            unfocusedTextColor = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.sendPasswordResetEmail()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicCrimson,
                            contentColor = ContentHighEmphasis
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ContentHighEmphasis,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send Reset Link",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
