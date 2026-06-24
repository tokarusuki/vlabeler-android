package com.sdercolin.vlabeler.android.data

import android.content.ContentResolver
import android.net.Uri

/** Small Android project model focused on oto.ini editing and linked sample names. */
data class VLabelerProject(
    val sourceUri: Uri? = null,
    val entries: List<OtoEntry> = emptyList(),
) {
    val sampleNames: List<String> = entries.map { it.sample }.distinct()
}

fun ContentResolver.readText(uri: Uri): String = openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
fun ContentResolver.writeText(uri: Uri, text: String) { openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } }
