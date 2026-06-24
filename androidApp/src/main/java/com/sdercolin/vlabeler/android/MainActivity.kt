package com.sdercolin.vlabeler.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sdercolin.vlabeler.android.audio.readWavPeaks
import com.sdercolin.vlabeler.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AndroidVLabelerApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidVLabelerApp() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    var project by remember { mutableStateOf(VLabelerProject()) }
    var selected by remember { mutableIntStateOf(0) }
    var waveform by remember { mutableStateOf<List<Float>>(emptyList()) }

    val openOto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val entries = withContext(Dispatchers.IO) { parseOtoIni(resolver.readText(uri)) }
            project = VLabelerProject(uri, entries)
            selected = 0
            snackbar.showSnackbar("Loaded ${entries.size} oto.ini entries")
        }
    }
    val saveOto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { resolver.writeText(uri, writeOtoIni(project.entries)) }
            snackbar.showSnackbar("Saved oto.ini")
        }
    }
    val openWav = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { readWavPeaks(it).peaks } }
            }.onSuccess { waveform = it }.onFailure { snackbar.showSnackbar(it.message ?: "Cannot load WAV") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("vLabeler Android") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = { openOto.launch(arrayOf("text/*", "application/octet-stream")) }, icon = {}, label = { Text("Open oto.ini") })
                NavigationBarItem(selected = false, onClick = { saveOto.launch("oto.ini") }, icon = {}, label = { Text("Save") })
                NavigationBarItem(selected = false, onClick = { openWav.launch(arrayOf("audio/wav", "audio/x-wav")) }, icon = {}, label = { Text("Wave") })
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(0.42f).fillMaxHeight()) {
                itemsIndexed(project.entries) { index, entry ->
                    ListItem(
                        headlineContent = { Text(entry.alias.ifBlank { "(blank alias)" }) },
                        supportingContent = { Text(entry.sample) },
                        modifier = Modifier.fillMaxWidth().clickable { selected = index },
                        colors = ListItemDefaults.colors(containerColor = if (index == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    )
                    HorizontalDivider()
                }
            }
            VerticalDivider()
            EntryEditor(
                entry = project.entries.getOrNull(selected),
                waveform = waveform,
                onChange = { changed -> project = project.copy(entries = project.entries.toMutableList().also { it[selected] = changed }) },
                modifier = Modifier.weight(0.58f).fillMaxHeight().padding(12.dp),
            )
        }
    }
}

@Composable
private fun EntryEditor(entry: OtoEntry?, waveform: List<Float>, onChange: (OtoEntry) -> Unit, modifier: Modifier = Modifier) {
    if (entry == null) { Box(modifier.padding(24.dp)) { Text("Open an oto.ini file to start editing.") }; return }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entry.sample, style = MaterialTheme.typography.titleMedium)
        EditableField("Alias", entry.alias) { onChange(entry.copy(alias = it)) }
        FloatField("Offset", entry.offset) { onChange(entry.copy(offset = it)) }
        FloatField("Consonant", entry.consonant) { onChange(entry.copy(consonant = it)) }
        FloatField("Cutoff", entry.cutoff) { onChange(entry.copy(cutoff = it)) }
        FloatField("Preutterance", entry.preutterance) { onChange(entry.copy(preutterance = it)) }
        FloatField("Overlap", entry.overlap) { onChange(entry.copy(overlap = it)) }
        Text("Waveform preview", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val mid = size.height / 2
            waveform.forEachIndexed { i, peak ->
                val x = i * size.width / waveform.size.coerceAtLeast(1)
                drawLine(Color(0xff4caf50), Offset(x, mid - peak * mid), Offset(x, mid + peak * mid))
            }
        }
    }
}

@Composable private fun EditableField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
@Composable private fun FloatField(label: String, value: Float, onChange: (Float) -> Unit) = OutlinedTextField(value = value.toString(), onValueChange = { onChange(it.toFloatOrNull() ?: value) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
