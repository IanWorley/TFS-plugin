package com.ianworley.tfvc.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.EditFileProvider
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment

class TfvcVcs(project: Project) : AbstractVcs(project, NAME) {
    private val changeProvider = TfvcChangeProvider(project)
    private val editFileProvider = TfvcEditFileProvider(project)
    private val checkinEnvironment = TfvcCheckinEnvironment(project)

    override fun getDisplayName(): String = NAME

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getEditFileProvider(): EditFileProvider = editFileProvider

    override fun getCheckinEnvironment(): CheckinEnvironment = checkinEnvironment

    companion object {
        const val NAME = "TFVC"
        val KEY: VcsKey by lazy {
            val constructor = VcsKey::class.java.getDeclaredConstructor(String::class.java)
            constructor.isAccessible = true
            constructor.newInstance(NAME)
        }
    }
}
