package com.ianworley.tfvc.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.EditFileProvider
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider

class TfvcVcs(project: Project) : AbstractVcs(project, NAME) {
    private val changeProvider = TfvcChangeProvider(project)
    private val editFileProvider = TfvcEditFileProvider(project)

    override fun getDisplayName(): String = NAME

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getEditFileProvider(): EditFileProvider = editFileProvider

    companion object {
        const val NAME = "TFVC"
        val KEY: VcsKey = VcsKey.create(NAME)
    }
}
