package com.esseanalytics.android.feature.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.network.dto.AppUserDto

// Owner-only. Mismo dato y mismos 3 endpoints que ya consume
// frontend/src/components/UsersPanel.tsx (búsqueda con debounce, filtro
// activos/dados de baja, toggle premium, dar de baja con confirmación) --
// ver UsersViewModel.
@Composable
fun UsersScreen(modifier: Modifier = Modifier, viewModel: UsersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val togglingId by viewModel.togglingUserId.collectAsState()
    val deactivatingId by viewModel.deactivatingUserId.collectAsState()
    var confirmDeactivateId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Buscar por usuario o email…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { viewModel.onStatusFilterChange(filter) },
                    label = { Text(if (filter == UserStatusFilter.ACTIVE) "Activos" else "Dados de baja") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        when (val current = state) {
            is UsersUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter,
            ) { CircularProgressIndicator() }

            is UsersUiState.Error -> PlaceholderScreen(
                title = "No se pudo cargar",
                note = current.message,
                icon = Icons.Filled.ErrorOutline,
                iconTint = MaterialTheme.colorScheme.error,
            )

            is UsersUiState.Success -> if (current.users.isEmpty()) {
                PlaceholderScreen(
                    title = if (query.isNotBlank()) "Sin resultados" else "No hay usuarios",
                    note = if (query.isNotBlank()) "Nada para \"$query\"." else "No hay usuarios en este filtro.",
                    icon = if (query.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.PeopleOutline,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.users, key = { it.id }) { user ->
                        UserCard(
                            user = user,
                            showingDeleted = statusFilter == UserStatusFilter.DELETED,
                            toggling = togglingId == user.id,
                            deactivating = deactivatingId == user.id,
                            confirmingDeactivate = confirmDeactivateId == user.id,
                            onToggleTier = { viewModel.toggleTier(user) },
                            onRequestDeactivate = { confirmDeactivateId = user.id },
                            onCancelDeactivate = { confirmDeactivateId = null },
                            onConfirmDeactivate = {
                                confirmDeactivateId = null
                                viewModel.deactivate(user)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: AppUserDto,
    showingDeleted: Boolean,
    toggling: Boolean,
    deactivating: Boolean,
    confirmingDeactivate: Boolean,
    onToggleTier: () -> Unit,
    onRequestDeactivate: () -> Unit,
    onCancelDeactivate: () -> Unit,
    onConfirmDeactivate: () -> Unit,
) {
    val isPremium = user.tier == "premium"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPremium) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.username.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    if (isPremium) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Premium",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(start = 4.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        user.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (!showingDeleted) {
                if (confirmingDeactivate) {
                    TextButton(onClick = onConfirmDeactivate, enabled = !deactivating) {
                        Text(if (deactivating) "…" else "Confirmar", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onCancelDeactivate) { Text("No") }
                } else {
                    Switch(checked = isPremium, onCheckedChange = { onToggleTier() }, enabled = !toggling)
                    IconButton(onClick = onRequestDeactivate) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Dar de baja",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
