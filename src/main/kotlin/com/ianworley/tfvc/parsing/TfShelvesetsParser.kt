package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.ShelvesetSummary

object TfShelvesetsParser {
    fun parse(output: String): List<ShelvesetSummary> =
        output.lineSequence()
            .map(String::trim)
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("---") &&
                    !line.startsWith("Shelveset", ignoreCase = true) &&
                    !line.startsWith("Owner", ignoreCase = true)
            }
            .mapNotNull { line ->
                val parts = line.split(Regex("""\s{2,}"""))
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                when {
                    parts.size >= 3 -> ShelvesetSummary(parts[0], parts[1], parts.drop(2).joinToString("  "))
                    parts.size == 2 -> ShelvesetSummary(parts[0], parts[1], "")
                    else -> null
                }
            }
            .toList()
}
