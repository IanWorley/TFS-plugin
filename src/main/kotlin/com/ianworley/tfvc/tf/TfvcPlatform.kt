package com.ianworley.tfvc.tf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo

object TfvcPlatform {
    fun canRunCommands(): Boolean = SystemInfo.isWindows || ApplicationManager.getApplication()?.isUnitTestMode == true
}
