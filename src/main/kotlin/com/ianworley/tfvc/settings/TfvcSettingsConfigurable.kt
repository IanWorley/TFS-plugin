package com.ianworley.tfvc.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class TfvcSettingsConfigurable(
    private val project: Project,
) : BoundSearchableConfigurable("TFVC", "com.ianworley.tfvc.settings") {
    override fun createPanel() = panel {
        val settings = TfvcSettingsState.getInstance(project)

        row("tf.exe path override") {
            textField()
                .bindText(settings::tfExecutablePathOverride)
                .columns(40)
                .comment("Leave blank to resolve tf.exe from PATH.")
        }

        row {
            checkBox("Automatically checkout files on first edit")
                .bindSelected(settings::autoCheckoutOnEdit)
        }

        row("Command timeout (seconds)") {
            intTextField(5..600)
                .bindIntText(settings::commandTimeoutSeconds)
                .columns(6)
        }

        row {
            checkBox("Verbose command logging")
                .bindSelected(settings::verboseCommandLogging)
                .comment("Mirrors tf.exe command execution and output summaries to the IDE VCS console.")
        }
    }
}
