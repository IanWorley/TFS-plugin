package com.ianworley.tfvc.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@State(
    name = "TfvcSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class TfvcSettingsState : PersistentStateComponent<TfvcSettingsState.State> {
    data class State(
        var tfExecutablePathOverride: String = "",
        var autoCheckoutOnEdit: Boolean = true,
        var commandTimeoutSeconds: Int = 60,
        var verboseCommandLogging: Boolean = false,
        var workspaceModeOverrides: MutableMap<String, String> = mutableMapOf(),
    )

    private var state = State()

    override fun getState(): State = state.copy(
        workspaceModeOverrides = state.workspaceModeOverrides.toMutableMap(),
    )

    override fun loadState(state: State) {
        this.state = state.copy(
            workspaceModeOverrides = state.workspaceModeOverrides.toMutableMap(),
        )
    }

    var tfExecutablePathOverride: String
        get() = state.tfExecutablePathOverride
        set(value) {
            state.tfExecutablePathOverride = value.trim()
        }

    var autoCheckoutOnEdit: Boolean
        get() = state.autoCheckoutOnEdit
        set(value) {
            state.autoCheckoutOnEdit = value
        }

    var commandTimeoutSeconds: Int
        get() = state.commandTimeoutSeconds
        set(value) {
            state.commandTimeoutSeconds = value.coerceIn(5, 600)
        }

    var verboseCommandLogging: Boolean
        get() = state.verboseCommandLogging
        set(value) {
            state.verboseCommandLogging = value
        }

    fun workspaceModePreference(root: java.nio.file.Path): WorkspaceModePreference =
        WorkspaceModePreference.fromStorage(state.workspaceModeOverrides[normalizeRootKey(root)])

    fun setWorkspaceModePreference(root: java.nio.file.Path, preference: WorkspaceModePreference) {
        val normalizedKey = normalizeRootKey(root)
        if (preference == WorkspaceModePreference.AUTO) {
            state.workspaceModeOverrides.remove(normalizedKey)
            return
        }

        state.workspaceModeOverrides[normalizedKey] = preference.name
    }

    private fun normalizeRootKey(root: java.nio.file.Path): String = root.toAbsolutePath().normalize().toString()

    companion object {
        fun getInstance(project: Project): TfvcSettingsState = project.service()
    }
}
