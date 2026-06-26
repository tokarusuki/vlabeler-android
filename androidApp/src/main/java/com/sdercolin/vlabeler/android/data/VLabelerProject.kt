package com.sdercolin.vlabeler.android.data

import android.content.ContentResolver
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Small Android project model focused on oto.ini editing and linked sample names. */
data class VLabelerProject(
    val sourceUri: Uri? = null,
    val entries: List<OtoEntry> = emptyList(),
    val encoding: OtoEncoding = OtoEncoding.UTF8,
) {
    val sampleNames: List<String> = entries.map { it.sample }.distinct()
}

enum class OtoEncoding(val charsetName: String, val label: String) {
    UTF8("UTF-8", "UTF-8"),
    SHIFT_JIS("Shift_JIS", "Shift-JIS"),
}

data class DecodedText(val text: String, val encoding: OtoEncoding)

fun ContentResolver.readOtoText(uri: Uri): DecodedText {
    val bytes = openInputStream(uri)?.use { it.readBytes() }.orEmpty()
    return bytes.decodeStrict(OtoEncoding.UTF8) ?: bytes.decodeStrict(OtoEncoding.SHIFT_JIS) ?: DecodedText(
        text = bytes.toString(Charset.forName(OtoEncoding.UTF8.charsetName)),
        encoding = OtoEncoding.UTF8,
    )
}

fun ContentResolver.writeOtoText(uri: Uri, text: String, encoding: OtoEncoding) {
    val charset = Charset.forName(encoding.charsetName)
    openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(charset)) }
}

private fun ByteArray.decodeStrict(encoding: OtoEncoding): DecodedText? = try {
    val charset = Charset.forName(encoding.charsetName)
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    DecodedText(decoder.decode(ByteBuffer.wrap(this)).toString(), encoding)
} catch (_: CharacterCodingException) {
    null
}
