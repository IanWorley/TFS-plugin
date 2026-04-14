package com.ianworley.tfvc.vcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.changes.ContentRevision

class TfvcPlaceholderContentRevision(
    private val filePath: FilePath,
    private val description: String,
) : ContentRevision {
    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = object : VcsRevisionNumber {
        override fun asString(): String = "TFVC_PENDING"

        override fun compareTo(other: VcsRevisionNumber): Int = 0
    }

    override fun getContent(): String = description
}
