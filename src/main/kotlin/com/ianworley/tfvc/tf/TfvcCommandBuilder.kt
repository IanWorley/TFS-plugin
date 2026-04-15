package com.ianworley.tfvc.tf

import com.intellij.openapi.vcs.FilePath
import java.nio.file.Files
import java.nio.file.Path

object TfvcCommandBuilder {
    fun workfold(path: Path): List<String> = listOf("workfold", path.toString())

    fun workspaces(): List<String> = listOf("workspaces", "/format:xml")

    fun status(scope: Path): List<String> = listOf("status", scope.toString(), "/recursive", "/format:detailed")

    fun checkout(paths: Collection<Path>): List<String> =
        listOf("checkout") + paths.map(Path::toString)

    fun add(paths: Collection<Path>): List<String> {
        val normalizedPaths = paths.map(Path::toString)
        val needsRecursive = paths.any { Files.isDirectory(it) }
        return buildList {
            add("add")
            addAll(normalizedPaths)
            if (needsRecursive) {
                add("/recursive")
            }
        }
    }

    fun checkin(items: Collection<FilePath>, comment: String?): List<String> =
        buildList {
            add("checkin")
            addAll(items.map(FilePath::getPath))
            if (!comment.isNullOrBlank()) {
                add("/comment:${comment.trim()}")
            }
            if (items.any(FilePath::isDirectory)) {
                add("/recursive")
            }
        }

    fun delete(items: Collection<FilePath>): List<String> =
        buildList {
            add("delete")
            addAll(items.map(FilePath::getPath))
            if (items.any(FilePath::isDirectory)) {
                add("/recursive")
            }
        }

    fun shelve(
        name: String,
        paths: Collection<Path>,
        comment: String?,
        replaceExisting: Boolean,
        moveChanges: Boolean,
    ): List<String> =
        buildList {
            add("shelve")
            if (replaceExisting) {
                add("/replace")
            }
            if (moveChanges) {
                add("/move")
            }
            if (!comment.isNullOrBlank()) {
                add("/comment:${comment.trim()}")
            }
            add(name)
            addAll(paths.map(Path::toString))
        }

    fun shelvesets(owner: String): List<String> = listOf("shelvesets", "/owner:$owner", "/format:brief")

    fun unshelve(name: String, owner: String, removeAfterUnshelve: Boolean): List<String> =
        buildList {
            add("unshelve")
            if (removeAfterUnshelve) {
                add("/move")
            }
            add("$name;$owner")
        }
}
