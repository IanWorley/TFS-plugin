package com.ianworley.tfvc.tf

import java.nio.file.Path

object TfvcPathMapper {
    fun toServerItem(workspace: WorkspaceContext, localPath: Path): String? {
        val normalizedRoot = workspace.localRoot.toAbsolutePath().normalize()
        val normalizedPath = localPath.toAbsolutePath().normalize()
        if (normalizedPath != normalizedRoot && !normalizedPath.startsWith(normalizedRoot)) {
            return null
        }

        val normalizedServerRoot = normalizeServerItem(workspace.serverPath)
        if (normalizedPath == normalizedRoot) {
            return normalizedServerRoot
        }

        val relativePath = normalizedRoot.relativize(normalizedPath)
            .joinToString("/") { it.toString() }
        return "$normalizedServerRoot/$relativePath"
    }

    fun toLocalPath(workspace: WorkspaceContext, serverItem: String): Path? {
        val normalizedServerRoot = normalizeServerItem(workspace.serverPath)
        val normalizedServerItem = normalizeServerItem(serverItem)
        if (!isWithinServerRoot(normalizedServerItem, normalizedServerRoot)) {
            return null
        }

        if (normalizedServerItem.length == normalizedServerRoot.length) {
            return workspace.localRoot.normalize()
        }

        val relativePath = normalizedServerItem.substring(normalizedServerRoot.length + 1)
        return relativePath.split('/')
            .filter(String::isNotEmpty)
            .fold(workspace.localRoot.normalize()) { path, segment -> path.resolve(segment) }
            .normalize()
    }

    private fun isWithinServerRoot(serverItem: String, serverRoot: String): Boolean =
        serverItem.equals(serverRoot, ignoreCase = true) ||
            (
                serverItem.length > serverRoot.length &&
                    serverItem.startsWith(serverRoot, ignoreCase = true) &&
                    serverItem[serverRoot.length] == '/'
                )

    private fun normalizeServerItem(serverItem: String): String {
        val normalized = serverItem.trim().replace('\\', '/')
        return if (normalized.length > 2) normalized.trimEnd('/') else normalized
    }
}
