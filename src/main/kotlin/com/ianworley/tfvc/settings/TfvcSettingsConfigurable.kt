package com.ianworley.tfvc.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.ianworley.tfvc.vcs.TfvcActionSupport
import com.ianworley.tfvc.vcs.TfvcStatusCache
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Path
import javax.swing.JComboBox

class TfvcSettingsConfigurable(
    private val project: Project,
) : BoundSearchableConfigurable("TFVC", "com.ianworley.tfvc.settings") {
    private val rootModeBoxes = linkedMapOf<Path, JComboBox<WorkspaceModePreference>>()

    override fun createPanel() = panel {
        val settings = TfvcSettingsState.getInstance(project)
        rootModeBoxes.clear()

        row("tf.exe path override") {
            textField()
                .bindText(settings::tfExecutablePathOverride)
                .comment("Leave blank to resolve tf.exe from PATH.")
        }

        row {
            checkBox("Automatically checkout files on first edit")
                .bindSelected(settings::autoCheckoutOnEdit)
        }

        row("Command timeout (seconds)") {
            intTextField(5..600)
                .bindIntText(settings::commandTimeoutSeconds)
        }

        row {
            checkBox("Verbose command logging")
                .bindSelected(settings::verboseCommandLogging)
                .comment("Mirrors tf.exe command execution and output summaries to the IDE VCS console.")
        }

        group("Workspace mode by root") {
            row {
                label("Auto uses the detected workspace type and falls back to Local when detection is unknown.")
            }

            val mappedRoots = TfvcActionSupport.mappedTfvcRoots(project)
                .map(Path::normalize)
            if (mappedRoots.isEmpty()) {
                row {
                    label("No mapped TFVC roots were detected for this project.")
                }
            } else {
                mappedRoots.forEach { root ->
                    val comboBox = JComboBox(WorkspaceModePreference.entries.toTypedArray())
                    comboBox.selectedItem = settings.workspaceModePreference(root)
                    rootModeBoxes[root] = comboBox

                    row(root.toString()) {
                        cell(comboBox)
                    }
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = TfvcSettingsState.getInstance(project)
        return super.isModified() || rootModeBoxes.any { (root, comboBox) ->
            settings.workspaceModePreference(root) != selectedPreference(comboBox)
        }
    }

    override fun reset() {
        super.reset()
        val settings = TfvcSettingsState.getInstance(project)
        rootModeBoxes.forEach { (root, comboBox) ->
            comboBox.selectedItem = settings.workspaceModePreference(root)
        }
    }

    override fun apply() {
        val settings = TfvcSettingsState.getInstance(project)
        val previousPreferences = rootModeBoxes.keys.associateWith(settings::workspaceModePreference)

        super.apply()

        rootModeBoxes.forEach { (root, comboBox) ->
            settings.setWorkspaceModePreference(root, selectedPreference(comboBox))
        }

        val overridesChanged = previousPreferences.any { (root, preference) ->
            settings.workspaceModePreference(root) != preference
        }
        if (!overridesChanged) {
            return
        }

        val statusCache = TfvcStatusCache.getInstance(project)
        statusCache.invalidate()
        TfvcActionSupport.mappedTfvcRoots(project).forEach(statusCache::requestRefresh)
    }

    private fun selectedPreference(comboBox: JComboBox<WorkspaceModePreference>): WorkspaceModePreference =
        comboBox.selectedItem as? WorkspaceModePreference ?: WorkspaceModePreference.AUTO
}
