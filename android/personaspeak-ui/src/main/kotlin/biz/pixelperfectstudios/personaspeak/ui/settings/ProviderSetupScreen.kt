package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.providers.ModelInfo
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterModels
import biz.pixelperfectstudios.personaspeak.providers.ProviderCatalog
import biz.pixelperfectstudios.personaspeak.providers.ProviderDef
import kotlinx.coroutines.launch

private val MinInteractiveHeight = 48.dp

/**
 * Brain setup surface: provider picker, API key entry, model override, and
 * connection status. The key field is local state only — it lives in
 * composition until handed to [onSave], which forwards it to the store.
 */
@Composable
fun ProviderSetupScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onSave: (providerId: String, apiKey: String, model: String?, onDone: () -> Unit) -> Unit,
    onClear: (onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuredId = (state.providerStatus as? ProviderStatusSummary.Configured)?.providerId
    var selectedProviderId by remember { mutableStateOf(configuredId ?: ProviderCatalog.all.first().id) }
    var apiKey by remember { mutableStateOf("") }
    var modelTexts by remember { mutableStateOf(mapOf<String, String>()) }
    val selectedDef = ProviderCatalog.byId(selectedProviderId)
    val modelText = modelTexts[selectedProviderId] ?: selectedDef?.defaultModel.orEmpty()
    val scope = rememberCoroutineScope()
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                Text(
                    text = "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "The Brain",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Connect the cloud brain behind your rewrites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_status_banner"),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Text(
                text = state.providerStatus.describe(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Text(
            text = "Provider",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        ProviderCatalog.all.forEach { def ->
            ProviderOptionRow(
                def = def,
                isSelected = def.id == selectedProviderId,
                onClick = { selectedProviderId = def.id },
                modifier = Modifier.testTag("personaspeak_provider_option_${def.id}"),
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_key_input"),
        )

        OutlinedTextField(
            value = modelText,
            onValueChange = { modelTexts = modelTexts + (selectedProviderId to it) },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_provider_model_input"),
        )

        if (selectedProviderId == "openrouter") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = {
                        modelsError = null
                        isLoadingModels = true
                        scope.launch {
                            val result = OpenRouterModels.fetch()
                            isLoadingModels = false
                            result
                                .onSuccess { models = it }
                                .onFailure { e ->
                                    android.util.Log.w("PsBrain", "models fetch failed: ${e.javaClass.name}: ${e.message}")
                                    modelsError =
                                        e.message ?: "Couldn't load the model list. Try again."
                                }
                        }
                    },
                    enabled = !isLoadingModels,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_provider_browse_models"),
                ) {
                    Text("Browse models…")
                }
                if (isLoadingModels) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading models…",
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
            TextButton(onClick = onDismiss) {
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

private fun ProviderStatusSummary.describe(): String = when (this) {
    ProviderStatusSummary.Unconfigured ->
        "Not connected — rewrites use the offline understudy."
    is ProviderStatusSummary.Configured ->
        "Connected: ${ProviderCatalog.byId(providerId)?.displayName ?: providerId}."
    ProviderStatusSummary.Unavailable ->
        "Secure storage is unavailable right now."
    ProviderStatusSummary.InvalidCredentials ->
        "The saved key could not be read and was cleared. Save it again."
}
