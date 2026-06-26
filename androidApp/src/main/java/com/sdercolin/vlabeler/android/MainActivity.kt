package com.sdercolin.vlabeler.android

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sdercolin.vlabeler.android.audio.readWavPeaks
import com.sdercolin.vlabeler.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AndroidVLabelerApp() } }
    }
}

private data class WaveformUiState(val uri: Uri? = null, val peaks: List<Float> = emptyList(), val durationMs: Int = 0)
private enum class OtoMarker(val label: String, val color: Color) {
    Offset("Offset", Color(0xfff44336)),
    Consonant("Consonant", Color(0xffff9800)),
    Cutoff("Cutoff", Color(0xff9c27b0)),
    Preutterance("Preutterance", Color(0xff2196f3)),
    Overlap("Overlap", Color(0xff00bcd4)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidVLabelerApp() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resolver = context.contentResolver
    var project by remember { mutableStateOf(VLabelerProject()) }
    var selected by remember { mutableIntStateOf(0) }
    var waveform by remember { mutableStateOf(WaveformUiState()) }
    var sampleUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackPositionMs by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            playbackPositionMs = player?.currentPosition ?: 0
            delay(33)
        }
    }

    fun loadWave(uri: Uri) = scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)!!.use { input ->
                    val wave = readWavPeaks(input)
                    WaveformUiState(uri, wave.peaks, ((wave.frameCount * 1000L) / wave.sampleRate).toInt())
                }
            }
        }.onSuccess { state ->
            waveform = state
            player?.release()
            player = MediaPlayer.create(context, uri)?.also { mp ->
                mp.setOnCompletionListener { isPlaying = false; playbackPositionMs = it.duration }
            }
            playbackPositionMs = 0
        }.onFailure { snackbar.showSnackbar(it.message ?: "Cannot load WAV") }
    }

    val openOto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) { resolver.readOtoText(uri) }
            val entries = parseOtoIni(decoded.text)
            project = VLabelerProject(uri, entries, decoded.encoding)
            selected = 0
            snackbar.showSnackbar("Loaded ${entries.size} entries as ${decoded.encoding.label}")
        }
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val root = DocumentFile.fromTreeUri(context, uri) ?: error("Cannot open folder")
                    val files = root.listFiles()
                    val oto = files.firstOrNull { it.name.equals("oto.ini", ignoreCase = true) } ?: error("Folder does not contain oto.ini")
                    val decoded = resolver.readOtoText(oto.uri)
                    val wavs = files.filter { it.isFile && it.name?.endsWith(".wav", ignoreCase = true) == true }.associate { it.name.orEmpty() to it.uri }
                    Triple(oto.uri, decoded, wavs)
                }
            }.onSuccess { (otoUri, decoded, wavs) ->
                val entries = parseOtoIni(decoded.text)
                project = VLabelerProject(otoUri, entries, decoded.encoding)
                sampleUris = wavs
                selected = 0
                entries.firstOrNull()?.sample?.let { wavs[it] }?.let(::loadWave)
                snackbar.showSnackbar("Loaded voicebank folder: ${entries.size} entries, ${wavs.size} WAV files")
            }.onFailure { snackbar.showSnackbar(it.message ?: "Cannot load folder") }
        }
    }
    val saveOto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { resolver.writeOtoText(uri, writeOtoIni(project.entries), project.encoding) }
            snackbar.showSnackbar("Saved oto.ini as ${project.encoding.label}")
        }
    }
    val openWav = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(::loadWave) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("vLabeler Android") }, actions = { Text(project.encoding.label, Modifier.padding(end = 16.dp)) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = false, onClick = { openFolder.launch(null) }, icon = {}, label = { Text("Voicebank") })
                NavigationBarItem(selected = false, onClick = { openOto.launch(arrayOf("text/*", "application/octet-stream")) }, icon = {}, label = { Text("Open oto") })
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
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = index
                            sampleUris[entry.sample]?.let(::loadWave)
                        },
                        colors = ListItemDefaults.colors(containerColor = if (index == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    )
                    HorizontalDivider()
                }
            }
            VerticalDivider()
            EntryEditor(
                entry = project.entries.getOrNull(selected),
                waveform = waveform,
                playbackPositionMs = playbackPositionMs,
                isPlaying = isPlaying,
                onPlayFrom = { ms -> player?.let { it.seekTo(ms); it.start(); isPlaying = true; playbackPositionMs = ms } },
                onTogglePlayback = { player?.let { if (it.isPlaying) { it.pause(); isPlaying = false } else { it.start(); isPlaying = true } } },
                onChange = { changed -> project = project.copy(entries = project.entries.toMutableList().also { it[selected] = changed }) },
                modifier = Modifier.weight(0.58f).fillMaxHeight().padding(12.dp),
            )
        }
    }
}

@Composable
private fun EntryEditor(entry: OtoEntry?, waveform: WaveformUiState, playbackPositionMs: Int, isPlaying: Boolean, onPlayFrom: (Int) -> Unit, onTogglePlayback: () -> Unit, onChange: (OtoEntry) -> Unit, modifier: Modifier = Modifier) {
    if (entry == null) { Box(modifier.padding(24.dp)) { Text("Open an oto.ini file or voicebank folder to start editing.") }; return }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entry.sample, style = MaterialTheme.typography.titleMedium)
        EditableField("Alias", entry.alias) { onChange(entry.copy(alias = it)) }
        WaveformEditor(entry, waveform, playbackPositionMs, onPlayFrom) { marker, value -> onChange(entry.withMarker(marker, value)) }
        Button(onClick = onTogglePlayback, enabled = waveform.uri != null) { Text(if (isPlaying) "Pause" else "Play") }
        FloatField("Offset", entry.offset) { onChange(entry.copy(offset = it)) }
        FloatField("Consonant", entry.consonant) { onChange(entry.copy(consonant = it)) }
        FloatField("Cutoff", entry.cutoff) { onChange(entry.copy(cutoff = it)) }
        FloatField("Preutterance", entry.preutterance) { onChange(entry.copy(preutterance = it)) }
        FloatField("Overlap", entry.overlap) { onChange(entry.copy(overlap = it)) }
    }
}

@Composable
private fun WaveformEditor(entry: OtoEntry, waveform: WaveformUiState, playbackPositionMs: Int, onPlayFrom: (Int) -> Unit, onMarkerChange: (OtoMarker, Float) -> Unit) {
    Text("Waveform: tap to play, drag colored OTO markers", style = MaterialTheme.typography.titleSmall)
    var dragging by remember { mutableStateOf<OtoMarker?>(null) }
    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .pointerInput(entry, waveform.durationMs) {
                detectTapGestures { pos -> if (waveform.durationMs > 0) onPlayFrom((pos.x / size.width * waveform.durationMs).toInt().coerceIn(0, waveform.durationMs)) }
            }
            .pointerInput(entry, waveform.durationMs) {
                detectDragGestures(
                    onDragStart = { pos -> dragging = OtoMarker.entries.minByOrNull { abs(markerX(it, entry, waveform.durationMs, size.width) - pos.x) } },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    val marker = dragging ?: return@detectDragGestures
                    change.consume()
                    val value = (change.position.x / max(size.width, 1).toFloat() * waveform.durationMs).coerceIn(0f, waveform.durationMs.toFloat())
                    onMarkerChange(marker, value)
                }
            },
    ) {
        val mid = size.height / 2
        waveform.peaks.forEachIndexed { i, peak ->
            val x = i * size.width / waveform.peaks.size.coerceAtLeast(1)
            drawLine(Color(0xff4caf50), Offset(x, mid - peak * mid), Offset(x, mid + peak * mid))
        }
        OtoMarker.entries.forEach { marker ->
            val x = markerX(marker, entry, waveform.durationMs, size.width)
            drawLine(marker.color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        }
        if (waveform.durationMs > 0) {
            val cursorX = playbackPositionMs.coerceIn(0, waveform.durationMs) * size.width / waveform.durationMs
            drawLine(Color.White, Offset(cursorX, 0f), Offset(cursorX, size.height), strokeWidth = 3f)
        }
    }
}

private fun markerX(marker: OtoMarker, entry: OtoEntry, durationMs: Int, width: Float): Float {
    if (durationMs <= 0) return 0f
    return (entry.markerValue(marker).coerceIn(0f, durationMs.toFloat()) / durationMs) * width
}

private fun OtoEntry.markerValue(marker: OtoMarker): Float = when (marker) {
    OtoMarker.Offset -> offset
    OtoMarker.Consonant -> offset + consonant
    OtoMarker.Cutoff -> if (cutoff < 0) (offset - cutoff) else cutoff
    OtoMarker.Preutterance -> offset + preutterance
    OtoMarker.Overlap -> offset + overlap
}

private fun OtoEntry.withMarker(marker: OtoMarker, absoluteMs: Float): OtoEntry = when (marker) {
    OtoMarker.Offset -> copy(offset = absoluteMs)
    OtoMarker.Consonant -> copy(consonant = absoluteMs - offset)
    OtoMarker.Cutoff -> copy(cutoff = absoluteMs)
    OtoMarker.Preutterance -> copy(preutterance = absoluteMs - offset)
    OtoMarker.Overlap -> copy(overlap = absoluteMs - offset)
}

@Composable private fun EditableField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
@Composable private fun FloatField(label: String, value: Float, onChange: (Float) -> Unit) = OutlinedTextField(value = value.toString(), onValueChange = { onChange(it.toFloatOrNull() ?: value) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
