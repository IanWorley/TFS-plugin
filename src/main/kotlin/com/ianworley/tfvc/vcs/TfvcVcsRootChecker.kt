package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.parsing.TfWorkfoldParser
import com.ianworley.tfvc.tf.TfvcPlatform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class TfvcVcsRootChecker : VcsRootChecker() {
    override fun getSupportedVcs(): VcsKey = TfvcVcs.KEY

    override fun isRoot(path: String): Boolean {
        if (!TfvcPlatform.canRunCommands()) {
            return false
        }

        val commandLine = GeneralCommandLine("tf.exe")
            .withParameters("workfold", path, "/noprompt")
            .withWorkDirectory(path)
            .withCharset(StandardCharsets.UTF_8)

        val output = CapturingProcessHandler(commandLine).runProcess(30_000)
        if (output.exitCode !in listOf(0, 1)) {
            return false
        }

        val workspace = TfWorkfoldParser.parse(output.stdout) ?: return false
        return workspace.localRoot == Path.of(path).normalize()
    }

    override fun isVcsDir(path: String): Boolean = false
}
