package dev.kosslan.compass.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.kosslan.compass.R
import dev.kosslan.compass.settings.MAX_LIGHTHOUSES
import dev.kosslan.compass.settings.MeshProblem
import kotlinx.coroutines.launch

/** PEM files rarely carry a certificate MIME type, so any file may be picked. */
private val CERTIFICATE_TYPES = arrayOf("*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshEditorSheet(
    draft: MeshDraft,
    model: SettingsViewModel,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pickCertificate =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(model::loadCertificate)
        }
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

            draft.lighthouses.forEachIndexed { index, url ->
                LighthouseField(
                    url = url,
                    onChange = { model.setLighthouse(index, it) },
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

            AuthKeyField(draft.authKey, onChange = model::setAuthKey)

            CertificateRow(
                loaded = draft.caPem != null,
                onChoose = { pickCertificate.launch(CERTIFICATE_TYPES) },
                onClear = model::clearCertificate,
            )

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
private fun LighthouseField(
    url: String,
    onChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = url,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.editor_lighthouse_url)) },
            placeholder = { Text(stringResource(R.string.editor_lighthouse_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )

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
private fun AuthKeyField(
    authKey: String,
    onChange: (String) -> Unit,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val icon = if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility
    val action = if (shown) R.string.editor_hide_auth_key else R.string.editor_show_auth_key

    OutlinedTextField(
        value = authKey,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.editor_auth_key)) },
        singleLine = true,
        visualTransformation =
            if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(painterResource(icon), contentDescription = stringResource(action))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CertificateRow(
    loaded: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    val summary = if (loaded) R.string.editor_ca_loaded else R.string.editor_ca_none

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.editor_ca), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (loaded) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }

        OutlinedButton(onClick = onChoose) { Text(stringResource(R.string.action_choose)) }
    }
}

@Composable
private fun MeshProblem.message(): String =
    when (this) {
        MeshProblem.NoLighthouse -> {
            stringResource(R.string.problem_no_lighthouse)
        }

        MeshProblem.InvalidUrl -> {
            stringResource(R.string.problem_invalid_url)
        }

        MeshProblem.InsecureUrl -> {
            stringResource(R.string.problem_insecure_url)
        }

        MeshProblem.DuplicateUrl -> {
            stringResource(R.string.problem_duplicate_url)
        }

        MeshProblem.TooManyLighthouses -> {
            pluralStringResource(R.plurals.problem_too_many, MAX_LIGHTHOUSES, MAX_LIGHTHOUSES)
        }

        MeshProblem.NoAuthKey -> {
            stringResource(R.string.problem_no_auth_key)
        }

        MeshProblem.InvalidCertificate -> {
            stringResource(R.string.problem_invalid_certificate)
        }

        MeshProblem.UnreadableCertificate -> {
            stringResource(R.string.problem_unreadable_certificate)
        }

        MeshProblem.LargeCertificate -> {
            stringResource(R.string.problem_large_certificate)
        }
    }
