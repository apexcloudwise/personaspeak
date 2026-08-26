package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val MinInteractiveHeight = 48.dp

/**
 * Screen for configuring the active AI brain provider, API key, model selection,
 * and custom base URL.
 */
@Composable
fun ProviderSetupScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onSave: (providerId: String, apiKey: String, model: String?, onDone: () -> Unit) -> Unit,
    onClear: (onDone: () -> Unit) -> Unit,
    onFetchModels: (suspend () -> Result<List<ModelInfo>>)? = null,
    modifier: Modifier = Modifier,
) {
    val configuredProviderId = (state.providerStatus as? ProviderStatusSummary.Configured)?.providerId
    var selectedProviderId by remember(configuredProviderId) {
        mutableStateOf(configuredProviderId ?: ProviderCatalog.openrouter.id)
    }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    var modelTexts by remember {
        mutableStateOf(
            ProviderCatalog.all.associate { it.id to it.defaultModel }
        )
    }

    var isFetchingModels by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selectedDef = ProviderCatalog.byId(selectedProviderId)
    val modelText = modelTexts[selectedProviderId] ?: selectedDef?.defaultModel ?: ""

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_setup_topbar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(MinInteractiveHeight)
                    .testTag("personaspeak_provider_setup_back"),
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "The Brain",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Active notice banner
        state.notice?.let { noticeText ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("personaspeak_provider_notice_banner"),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = noticeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_status_card"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.providerStatus.describe(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Provider Options Section
        Text(
            text = "CHOOSE PROVIDER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderCatalog.all.forEach { def ->
                ProviderOptionRow(
                    def = def,
                    isSelected = def.id == selectedProviderId,
                    onClick = { selectedProviderId = def.id },
                    modifier = Modifier.testTag("personaspeak_provider_option_${def.id}"),
                )
            }
        }

        // API Key input
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            placeholder = {
                Text(
                    if (state.providerStatus is ProviderStatusSummary.Configured) {
                        "•••••••••••••••• (leave blank to keep)"
                    } else {
                        "Paste provider API key"
                    }
                )
            },
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(
                    onClick = { showApiKey = !showApiKey },
                    modifier = Modifier.heightIn(min = MinInteractiveHeight),
                ) {
                    Text(if (showApiKey) "Hide" else "Show")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_key_input"),
        )

        // Model field & picker
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = modelText,
                onValueChange = { updated ->
                    modelTexts = modelTexts + (selectedProviderId to updated)
                },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("personaspeak_provider_model_input"),
            )

            if (selectedProviderId == ProviderCatalog.openrouter.id) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (onFetchModels != null) {
                                isFetchingModels = true
                                modelsError = null
                                scope.launch {
                                    val result = onFetchModels()
                                    isFetchingModels = false
                                    result.fold(
                                        onSuccess = { fetched ->
                                            models = fetched
                                        },
                                        onFailure = { err ->
                                            modelsError = "Failed to load models: ${err.message}"
                                        },
                                    )
                                }
                            }
                        },
                        enabled = !isFetchingModels,
                        modifier = Modifier
                            .heightIn(min = MinInteractiveHeight)
                            .testTag("personaspeak_provider_browse_models"),
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Browse models…")
                    }
                    Text(
                        text = "Public catalog (free models marked)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            modelsError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .testTag("personaspeak_provider_models_error"),
                )
            }
        }

        // Key URL link
        selectedDef?.let { def ->
            SelectionContainer {
                Text(
                    text = "Get a key: ${def.keyUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .testTag("personaspeak_provider_key_url"),
                )
            }
        }

        // Save Button
        Button(
            onClick = { onSave(selectedProviderId, apiKey, modelText) { apiKey = "" } },
            enabled = apiKey.isNotBlank() && selectedProviderId.isNotBlank() && !state.isSavingProvider,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_provider_save"),
        ) {
            Text(if (state.isSavingProvider) "Saving…" else "Save")
        }

        // Remove Key Button
        if (state.providerStatus is ProviderStatusSummary.Configured) {
            TextButton(
                onClick = { onClear { apiKey = "" } },
                enabled = !state.isSavingProvider,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_provider_remove"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Remove key")
            }
        }

        // Privacy note
        Text(
            text = "🔒 Privacy: Prompts are sent directly from your device to the selected provider. No drafts or credentials are ever stored off-device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("personaspeak_provider_privacy_notice"),
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (models.isNotEmpty()) {
        OpenRouterModelPickerDialog(
            models = models,
            onDismiss = { models = emptyList() },
            onSelect = { info ->
                modelTexts = modelTexts + (selectedProviderId to info.id)
                models = emptyList()
            },
        )
    }
}

/** Searchable OpenRouter model picker shown after a successful catalog fetch. */
@Composable
private fun OpenRouterModelPickerDialog(
    models: List<ModelInfo>,
    onDismiss: () -> Unit,
    onSelect: (ModelInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) {
        models
    } else {
        models.filter {
            it.id.contains(trimmed, ignoreCase = true) ||
                it.name.contains(trimmed, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a model") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personaspeak_model_search_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            onClick = { onSelect(model) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = MinInteractiveHeight),
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (model.isFree) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderOptionRow(
    def: ProviderDef,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = def.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${def.defaultModel} · ${def.defaultBaseUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
