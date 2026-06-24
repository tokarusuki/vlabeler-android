package com.sdercolin.vlabeler.android.data

/** Android-reusable representation of one UTAU oto.ini line. */
data class OtoEntry(
    val sample: String,
    val alias: String,
    val offset: Float,
    val consonant: Float,
    val cutoff: Float,
    val preutterance: Float,
    val overlap: Float,
) {
    fun toOtoLine(): String = "$sample=$alias,$offset,$consonant,$cutoff,$preutterance,$overlap"

    companion object {
        fun parse(line: String): OtoEntry? {
            if (line.isBlank() || !line.contains('=')) return null
            val sample = line.substringBefore('=').trim()
            val fields = line.substringAfter('=').split(',')
            if (fields.size < 6) return null
            return OtoEntry(
                sample = sample,
                alias = fields[0].trim(),
                offset = fields[1].toFloatOrNull() ?: 0f,
                consonant = fields[2].toFloatOrNull() ?: 0f,
                cutoff = fields[3].toFloatOrNull() ?: 0f,
                preutterance = fields[4].toFloatOrNull() ?: 0f,
                overlap = fields[5].toFloatOrNull() ?: 0f,
            )
        }
    }
}

fun parseOtoIni(text: String): List<OtoEntry> = text.lineSequence().mapNotNull(OtoEntry::parse).toList()
fun writeOtoIni(entries: List<OtoEntry>): String = entries.joinToString(separator = "\n") { it.toOtoLine() } + "\n"
