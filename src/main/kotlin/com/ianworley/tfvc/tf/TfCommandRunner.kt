package com.ianworley.tfvc.tf

import com.ianworley.tfvc.settings.TfvcSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.system.measureTimeMillis

class TfCommandRunner(
    private val project: Project,
) {
    fun run(
        args: List<String>,
        workingDirectory: Path? = null,
        timeoutSeconds: Int = TfvcSettingsState.getInstance(project).commandTimeoutSeconds,
    ): TfCommandResult {
        if (!TfvcPlatform.canRunCommands()) {
            return TfCommandResult(
                exitCode = 100,
                stdout = "",
                stderr = "TFVC support is available on Windows only. Configure tf.exe externally and retry on Windows.",
                durationMs = 0,
            )
        }

        val settings = TfvcSettingsState.getInstance(project)
        val executable = settings.tfExecutablePathOverride.ifBlank { "tf.exe" }
        val effectiveArgs = if (args.any { it.equals("/noprompt", ignoreCase = true) }) args else args + "/noprompt"
        val commandLine = GeneralCommandLine(executable)
            .withParameters(effectiveArgs)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        if (workingDirectory != null) {
            commandLine.withWorkDirectory(workingDirectory)
        }

        val handler = CapturingProcessHandler(commandLine)
        val timeoutMillis = timeoutSeconds * 1000
        var capturedOutput = handler.runProcess(timeoutMillis)
        val durationMs = measureTimeMillis {
            if (capturedOutput.isTimeout) {
                capturedOutput = capturedOutput.apply {
                    appendStderr("Command timed out after ${timeoutSeconds}s.")
                }
            }
        }

        val result = TfCommandResult(
            exitCode = if (capturedOutput.isTimeout) 100 else capturedOutput.exitCode,
            stdout = capturedOutput.stdout.trim(),
            stderr = capturedOutput.stderr.trim(),
            durationMs = durationMs,
        )

        if (settings.verboseCommandLogging) {
            val stdoutSummary = result.stdout.lineSequence().firstOrNull().orEmpty()
            val stderrSummary = result.stderr.lineSequence().firstOrNull().orEmpty()
            TfvcNotifications.logToConsole(
                project,
                "tf ${effectiveArgs.joinToString(" ")} [exit=${result.exitCode}] stdout=\"$stdoutSummary\" stderr=\"$stderrSummary\"",
            )
        }

        return result
    }

    companion object {
        fun getInstance(project: Project): TfCommandRunner = project.service()
    }
}
