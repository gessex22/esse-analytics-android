package com.esseanalytics.android.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onServerClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val serverLabel by viewModel.serverLabel.collectAsState()
    var registerMode by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoginLogo()
            Text(
                text = if (registerMode) "Crear cuenta" else "Iniciar sesión",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 18.dp),
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                LoginTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = "Usuario",
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LoginTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Contraseña",
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    password = true,
                )
                if (registerMode) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LoginTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Repetir contraseña",
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                        },
                        password = true,
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Button(
                onClick = {
                    if (registerMode && state.password != confirmPassword) {
                        viewModel.setPasswordMismatch()
                    } else if (registerMode) {
                        viewModel.register(state.username, state.password)
                    } else {
                        viewModel.login()
                    }
                },
                enabled = !state.loading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .width(160.dp)
                    .height(46.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = if (registerMode) "Crear cuenta" else "Entrar",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TextButton(
                onClick = {
                    registerMode = !registerMode
                    confirmPassword = ""
                },
            ) {
                Text(if (registerMode) "Ya tengo una cuenta" else "Crear cuenta")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = onServerClick,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Servidor: $serverLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoginLogo() {
    val context = LocalContext.current
    val logoResource = remember(context.packageName) {
        context.resources.getIdentifier(
            "ic_launcher_foreground",
            "drawable",
            context.packageName,
        )
    }
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF4A701))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (logoResource != 0) {
            Image(
                painter = painterResource(logoResource),
                contentDescription = "Logo de EsseAnalytics",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("EA", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable () -> Unit,
    password: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = leadingIcon,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    )
}
