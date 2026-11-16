package jp.kyoto.sync.parley.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.kyoto.sync.parley.R
import jp.kyoto.sync.parley.core.Keys
import jp.kyoto.sync.parley.core.Oberflaeche
import jp.kyoto.sync.parley.core.Recognition
import jp.kyoto.sync.parley.core.TranscriptionModel
import jp.kyoto.sync.parley.core.Translation
import jp.kyoto.sync.parley.core.Voice

/** Erkennung, Übersetzung und Stimme einzeln wählen; die Schlüssel sind fest eingebaut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: TranslationViewModel,
    oberflaeche: Oberflaeche,
    onOberflaeche: (Oberflaeche) -> Unit,
    onDone: () -> Unit,
) {
    val setup by vm.setup.collectAsState()
    val transcription by vm.transcriptionModel.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cd_settings)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sprachnamen in der eigenen Sprache, damit man zurückfindet, auch wenn man die andere nicht liest.
            Picker(stringResource(R.string.oberflaeche_label), oberflaeche, Oberflaeche.entries, {
                when (it) {
                    Oberflaeche.SR -> "Srpski"
                    Oberflaeche.DE -> "Deutsch"
                }
            }) { if (it != oberflaeche) onOberflaeche(it) }
            Picker(stringResource(R.string.recognition_label), setup.recognition, Recognition.entries, { it.label }) {
                vm.setSetup(setup.copy(recognition = it))
            }
            if (setup.allInOne) {
                Hint(stringResource(R.string.all_in_one_hint))
                if (setup.recognition != Recognition.GEMINI_LIVE) {
                    Picker(stringResource(R.string.transcription_model_label), transcription, TranscriptionModel.entries, { it.id }) {
                        vm.setTranscriptionModel(it)
                    }
                }
            } else {
                Picker(stringResource(R.string.voice_label), setup.voice, Voice.entries, { it.label }) {
                    vm.setSetup(setup.copy(voice = it))
                }
                if (setup.usesTranslator) {
                    Picker(stringResource(R.string.translation_label), setup.translation, Translation.entries, { it.label }) {
                        vm.setSetup(setup.copy(translation = it))
                    }
                } else {
                    Hint(stringResource(R.string.gemini_voice_hint))
                }
            }
            val missing = Keys.missing(setup)
            if (missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.error_missing_keys, missing.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> Picker(label: String, selected: T, options: List<T>, name: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name(selected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(name(o)) }, onClick = { onSelect(o); expanded = false })
                }
            }
        }
    }
}
