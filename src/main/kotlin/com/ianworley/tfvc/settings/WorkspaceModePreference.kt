package com.ianworley.tfvc.settings

enum class WorkspaceModePreference(
    private val displayName: String,
) {
    AUTO("Auto"),
    LOCAL("Local"),
    SERVER("Server"),
    ;

    override fun toString(): String = displayName

    companion object {
        fun fromStorage(value: String?): WorkspaceModePreference =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}
