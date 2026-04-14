package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.PendingChange
import com.ianworley.tfvc.tf.PendingChangeType
import java.nio.file.Path
import java.nio.file.Paths

object TfStatusParser {
    private val keyValuePattern = Regex("""^\s*([^:]+?)\s*:\s*(.*?)\s*$""")

    fun parse(output: String): List<PendingChange> {
        val normalized = output.trim()
        if (normalized.isEmpty() || normalized.contains("There are no pending changes", ignoreCase = true)) {
            return emptyList()
        }

        return normalized.split(Regex("""\r?\n\s*\r?\n"""))
            .mapNotNull(::parseBlock)
    }

    private fun parseBlock(block: String): PendingChange? {
        var localPath: Path? = null
        var changeDescriptor = ""
        var user = ""
        var locked = false
        var candidate = false

        block.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                keyValuePattern.matchEntire(line)?.let { match ->
                    val key = match.groupValues[1].trim().lowercase()
                    val value = match.groupValues[2].trim()
                    when {
                        key.startsWith("local item") -> localPath = Paths.get(value).normalize()
                        key.startsWith("change") -> changeDescriptor = value
                        key.startsWith("user") -> user = value
                        key.startsWith("lock") -> locked = value.isNotBlank() && !value.equals("none", ignoreCase = true)
                        key.startsWith("candidate") -> candidate = value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                    }
                }
            }

        val resolvedPath = localPath ?: return null
        val tokens = changeDescriptor.split(',', ';')
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (tokens.any { it.contains("candidate", ignoreCase = true) }) {
            candidate = true
        }

        val changeType = tokens
            .map(::mapChangeType)
            .firstOrNull { it != PendingChangeType.OTHER }
            ?: if (candidate) PendingChangeType.ADD else PendingChangeType.OTHER

        return PendingChange(
            localPath = resolvedPath,
            changeType = changeType,
            isCandidate = candidate,
            user = user,
            locked = locked,
        )
    }

    private fun mapChangeType(token: String): PendingChangeType = when {
        token.contains("edit", ignoreCase = true) -> PendingChangeType.EDIT
        token.contains("add", ignoreCase = true) -> PendingChangeType.ADD
        token.contains("delete", ignoreCase = true) -> PendingChangeType.DELETE
        token.contains("rename", ignoreCase = true) -> PendingChangeType.RENAME
        token.contains("branch", ignoreCase = true) -> PendingChangeType.BRANCH
        token.contains("undelete", ignoreCase = true) -> PendingChangeType.UNDELETE
        else -> PendingChangeType.OTHER
    }
}
