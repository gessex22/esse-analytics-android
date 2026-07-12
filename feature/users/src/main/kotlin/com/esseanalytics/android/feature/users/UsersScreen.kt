package com.esseanalytics.android.feature.users

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 2, owner-only: GET /api/auth/users (core:network AuthApi, agregar el
// método cuando se construya esta pantalla).
@Composable
fun UsersScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Usuarios",
        note = "Administración de usuarios — Fase 2",
        modifier = modifier,
    )
}
