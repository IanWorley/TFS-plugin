package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.parsing.TfWorkfoldParser
import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.TfvcPlatform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.ProjectManager
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

        val commandLine = GeneralCommandLine(resolveExecutable(path))
            .withParameters("workfold", path, "/noprompt")
            .withWorkDirectory(path)
            .withCharset(StandardCharsets.UTF_8)

        val output = try {
            CapturingProcessHandler(commandLine).runProcess(30_000)
        } catch (_: Exception) {
            return false
        }
        if (output.exitCode !in listOf(0, 1)) {
            return false
        }

        val workspace = TfWorkfoldParser.parse(output.stdout) ?: return false
        return workspace.localRoot == Path.of(path).normalize()
    }

    override fun isVcsDir(path: String): Boolean = false

    private fun resolveExecutable(path: String): String {
        val normalizedPath = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()
        val project = ProjectManager.getInstance().openProjects.firstOrNull { openProject ->
            val projectPath = openProject.basePath
                ?.takeIf(String::isNotBlank)
                ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
                ?: return@firstOrNull false

            normalizedPath != null &&
                (normalizedPath.startsWith(projectPath) || projectPath.startsWith(normalizedPath))
        }

        return project
            ?.let(TfvcSettingsState::getInstance)
            ?.tfExecutablePathOverride
            .orEmpty()
            .ifBlank { "tf.exe" }
    }
}
