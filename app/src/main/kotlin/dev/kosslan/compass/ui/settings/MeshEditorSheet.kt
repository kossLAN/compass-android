package dev.kosslan.compass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.kosslan.compass.R
import dev.kosslan.compass.settings.Lighthouse
import dev.kosslan.compass.settings.MAX_LIGHTHOUSES
import dev.kosslan.compass.settings.MeshProblem
import dev.kosslan.compass.ui.theme.mono
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshEditorSheet(
    draft: MeshDraft,
    model: SettingsViewModel,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = if (draft.saved) R.string.editor_edit_title else R.string.editor_add_title

    LaunchedEffect(draft.done) {
        if (draft.done) {
            sheet.hide()
            model.dismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = model::dismiss, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.editor_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            draft.lighthouses.forEachIndexed { index, lighthouse ->
                LighthouseFields(
                    lighthouse = lighthouse,
                    onEndpointChange = { model.setEndpoint(index, it) },
                    onPublicKeyChange = { model.setPublicKey(index, it) },
                    onRemove =
                        {
                            model.removeLighthouse(
                                index,
                            )
                        }.takeIf { draft.lighthouses.size > 1 },
                )
            }

            if (draft.lighthouses.size < MAX_LIGHTHOUSES) {
                TextButton(onClick = model::addLighthouse) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.editor_add_lighthouse),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            draft.problem?.let { problem ->
                Text(
                    text = problem.message(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (draft.saved) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch { sheet.hide() }.invokeOnCompletion { model.dismiss() }
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = model::save) { Text(stringResource(R.string.action_save)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LighthouseFields(
    lighthouse: Lighthouse,
    onEndpointChange: (String) -> Unit,
    onPublicKeyChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = lighthouse.endpoint,
                onValueChange = onEndpointChange,
                label = { Text(stringResource(R.string.editor_lighthouse_endpoint)) },
                placeholder = { Text(stringResource(R.string.editor_lighthouse_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lighthouse.publicKey,
                onValueChange = onPublicKeyChange,
                label = { Text(stringResource(R.string.editor_lighthouse_key)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.mono,
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.editor_remove_lighthouse),
                )
            }
        }
    }
}

@Composable
private fun MeshProblem.message(): String =
    when (this) {
        MeshProblem.NoLighthouse -> {
            stringResource(R.string.problem_no_lighthouse)
        }

        MeshProblem.InvalidEndpoint -> {
            stringResource(R.string.problem_invalid_endpoint)
        }

        MeshProblem.InvalidPublicKey -> {
            stringResource(R.string.problem_invalid_public_key)
        }

        MeshProblem.DuplicateLighthouse -> {
            stringResource(R.string.problem_duplicate_lighthouse)
        }

        MeshProblem.TooManyLighthouses -> {
            pluralStringResource(R.plurals.problem_too_many, MAX_LIGHTHOUSES, MAX_LIGHTHOUSES)
        }
    }
