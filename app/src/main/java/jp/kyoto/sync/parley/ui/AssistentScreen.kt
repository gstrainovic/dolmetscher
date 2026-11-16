package jp.kyoto.sync.parley.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.kyoto.sync.parley.R
import jp.kyoto.sync.parley.audio.Testton
import jp.kyoto.sync.parley.core.Assistent
import jp.kyoto.sync.parley.core.Schritt

/**
 * Einrichtungs-Assistent: steht vor der Hauptseite, solange Bluetooth aus ist, kein Kopfhörer und keine Brille
 * verbunden ist oder der Funktionstest für das verbundene Gerät fehlt. Es gibt keinen Knopf zum Überspringen.
 *
 * @param geraetId Kennung des verbundenen Kopfhörers (für den Test), null ohne Gerät.
 */
@Composable
fun AssistentScreen(
    schritt: Schritt,
    geraetName: String?,
    geraetId: Int?,
    onBluetoothEin: () -> Unit,
    onKoppeln: () -> Unit,
    onTestBestanden: (Int) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.assistent_titel), style = MaterialTheme.typography.headlineMedium)
            if (geraetName != null) {
                Text(stringResource(R.string.assistent_geraet, geraetName), style = MaterialTheme.typography.bodyMedium)
            }
            when (schritt) {
                Schritt.BLUETOOTH -> {
                    Text(stringResource(R.string.assistent_bluetooth_text))
                    Button(onClick = onBluetoothEin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.assistent_bluetooth_knopf))
                    }
                }
                Schritt.KOPPELN -> {
                    Text(stringResource(R.string.assistent_koppeln_text))
                    Button(onClick = onKoppeln, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.assistent_koppeln_knopf))
                    }
                }
                Schritt.TEST -> if (geraetId != null) Funktionstest(geraetId, onTestBestanden)
                Schritt.FERTIG -> Unit
            }
        }
    }
}

@Composable
private fun Funktionstest(geraetId: Int, onTestBestanden: (Int) -> Unit) {
    val ctx = LocalContext.current
    // Neues Gerät, neuer Test.
    var tonGehoert by remember(geraetId) { mutableStateOf(false) }
    var stimmeGehoert by remember(geraetId) { mutableStateOf(false) }
    var kaputt by remember(geraetId) { mutableStateOf(false) }
    var ohneMikrofon by remember(geraetId) { mutableStateOf(false) }

    DisposableEffect(geraetId) {
        val stopp = Testton.hoereZu(ctx) { stimmeGehoert = true }
        ohneMikrofon = stopp == null
        onDispose { stopp?.invoke() }
    }
    LaunchedEffect(tonGehoert, stimmeGehoert) {
        if (Assistent.testBestanden(tonGehoert, stimmeGehoert)) onTestBestanden(geraetId)
    }

    Text(stringResource(R.string.assistent_test_text))
    OutlinedButton(
        onClick = { kaputt = !Testton.spiele(ctx) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.assistent_test_ton_knopf)) }
    if (kaputt) Text(stringResource(R.string.assistent_test_kein_geraet), color = MaterialTheme.colorScheme.error)
    Button(
        onClick = { tonGehoert = true },
        enabled = !tonGehoert,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.assistent_test_gehoert)) }

    Spacer(Modifier.size(8.dp))
    Haken(tonGehoert, stringResource(R.string.assistent_test_ton_ok))
    when {
        ohneMikrofon -> Text(
            stringResource(R.string.assistent_test_kein_mikrofon),
            color = MaterialTheme.colorScheme.error,
        )
        stimmeGehoert -> Haken(true, stringResource(R.string.assistent_test_stimme_ok))
        else -> Haken(false, stringResource(R.string.assistent_test_sprechen))
    }
}

@Composable
private fun Haken(erledigt: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (erledigt) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (erledigt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
