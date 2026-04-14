package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.WorkspaceContext
import com.ianworley.tfvc.tf.WorkspaceType
import java.nio.file.Path
import java.nio.file.Paths

object TfWorkfoldParser {
    private val mappingPattern = Regex("""^\s*(\$/.+?)\s*:\s*(.+?)\s*$""")
    private val workspacePattern = Regex("""^\s*Workspace\s*:\s*(.+?)(?:;(.*))?\s*$""", RegexOption.IGNORE_CASE)
    private val collectionPattern = Regex("""^\s*Collection\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val localFolderPattern = Regex("""^\s*Local\s+folder\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val serverFolderPattern = Regex("""^\s*Server\s+folder\s*:\s*(\$/.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val typePattern = Regex("""^\s*Workspace\s+type\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(output: String): WorkspaceContext? {
        if (output.contains("There is no working folder mapping", ignoreCase = true)) {
            return null
        }

        var localRoot: Path? = null
        var serverPath = ""
        var workspaceName = ""
        var owner = ""
        var collectionUrl = ""
        var workspaceType = WorkspaceType.UNKNOWN

        output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { line ->
                workspacePattern.matchEntire(line)?.let {
                    workspaceName = it.groupValues[1].trim()
                    owner = it.groupValues.getOrElse(2) { "" }.trim()
                    return@forEach
                }
                collectionPattern.matchEntire(line)?.let {
                    collectionUrl = it.groupValues[1].trim()
                    return@forEach
                }
                typePattern.matchEntire(line)?.let {
                    workspaceType = parseWorkspaceType(it.groupValues[1])
                    return@forEach
                }
                localFolderPattern.matchEntire(line)?.let {
                    localRoot = Paths.get(it.groupValues[1].trim()).normalize()
                    return@forEach
                }
                serverFolderPattern.matchEntire(line)?.let {
                    serverPath = it.groupValues[1].trim()
                    return@forEach
                }
                mappingPattern.matchEntire(line)?.let {
                    serverPath = it.groupValues[1].trim()
                    localRoot = Paths.get(it.groupValues[2].trim()).normalize()
                }
            }

        val root = localRoot ?: return null
        if (serverPath.isBlank()) {
            return null
        }

        return WorkspaceContext(
            localRoot = root,
            serverPath = serverPath,
            workspaceName = workspaceName,
            owner = owner,
            collectionUrl = collectionUrl,
            workspaceType = workspaceType,
        )
    }

    private fun parseWorkspaceType(value: String): WorkspaceType = when {
        value.contains("local", ignoreCase = true) -> WorkspaceType.LOCAL
        value.contains("server", ignoreCase = true) -> WorkspaceType.SERVER
        else -> WorkspaceType.UNKNOWN
    }
}
