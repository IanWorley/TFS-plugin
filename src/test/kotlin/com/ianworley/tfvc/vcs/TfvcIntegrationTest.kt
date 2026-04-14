package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.parsing.TfShelvesetsParser
import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TfvcIntegrationTest : BasePlatformTestCase() {
    private lateinit var fakeTfDir: Path
    private lateinit var responsesDir: Path
    private lateinit var logFile: Path
    private lateinit var workspaceRoot: Path

    override fun getTestDataPath(): String = Paths.get("src/test/resources").toAbsolutePath().toString()

    override fun setUp() {
        super.setUp()

        workspaceRoot = Files.createTempDirectory("tfvc-workspace")
        Files.createDirectories(workspaceRoot)
        fakeTfDir = Files.createTempDirectory("tfvc-fake")
        responsesDir = fakeTfDir.resolve("responses")
        Files.createDirectories(responsesDir)
        logFile = fakeTfDir.resolve("commands.log")
        Files.createDirectories(fakeTfDir)
        Files.writeString(fakeTfDir.resolve("tf.exe"), fakeTfScript())
        fakeTfDir.resolve("tf.exe").toFile().setExecutable(true)

        val settings = TfvcSettingsState.getInstance(project)
        settings.tfExecutablePathOverride = fakeTfDir.resolve("tf.exe").toString()
        settings.autoCheckoutOnEdit = true
        settings.commandTimeoutSeconds = 15

        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            workspaceRoot.toString(),
            workspaceRoot.toRealPath().toString(),
            fakeTfDir.toString(),
            fakeTfDir.toRealPath().toString(),
        )
        writeWorkspaceDiscoveryOutputs()
    }

    fun `test workspace discovery resolves mapped root`() {
        val discovered = TfvcWorkspaceService.getInstance(project).findWorkspaceFor(workspaceRoot)

        assertNotNull(discovered)
        assertEquals(workspaceRoot.normalize(), discovered!!.localRoot)
        assertEquals("$/Sample", discovered.serverPath)
    }

    fun `test manual checkout runs tf checkout`() {
        val file = createWorkspaceFile("tracked.txt", "hello")

        writeResponse("checkout.stdout", "")
        TfvcEditFileProvider(project).checkoutFiles(listOf(file))

        assertThatLogContains("checkout ${file.toNioPath()}")
    }

    fun `test auto checkout on first edit runs checkout`() {
        val file = createWorkspaceFile("auto.txt", "hello")
        writeResponse("status.stdout", "There are no pending changes.")
        writeResponse("checkout.stdout", "")

        TfvcStartupActivity().runActivity(project)
        myFixture.configureFromExistingVirtualFile(file)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "x")
        }
        FileDocumentManager.getInstance().saveDocument(myFixture.editor.document)

        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for tf checkout",
            { logText().contains("checkout ${file.toNioPath()}") },
            5,
        )
    }

    fun `test add files runs tf add`() {
        val file = createWorkspaceFile("new.txt", "content")
        writeResponse("add.stdout", "")

        TfvcVfsListener(project).addFiles(listOf(file))

        assertThatLogContains("add ${file.toNioPath()}")
    }

    fun `test shelveset listing and unshelve flow use fake tf`() {
        writeResponse(
            "shelvesets.stdout",
            """
            Shelveset      Owner      Comment
            -----------    --------   --------------------
            shelf-one      ian        First shelveset
            """.trimIndent(),
        )
        writeResponse("unshelve.stdout", "")

        val summaries = TfShelvesetsParser.parse(
            TfCommandRunner.getInstance(project).run(
                TfvcCommandBuilder.shelvesets("ian"),
                workspaceRoot,
            ).stdout,
        )
        assertEquals(1, summaries.size)

        val result = TfCommandRunner.getInstance(project).run(
            TfvcCommandBuilder.unshelve(summaries.single().name, summaries.single().owner, true),
            workspaceRoot,
        )

        assertTrue(result.isSuccessfulLike)
        assertThatLogContains("shelvesets /owner:ian /format:brief /noprompt")
        assertThatLogContains("unshelve /move shelf-one;ian /noprompt")
    }

    fun `test partial success exit code is accepted for checkout`() {
        val file = createWorkspaceFile("partial.txt", "hello")
        writeResponse("checkout.exit", "1")
        writeResponse("checkout.stdout", "Checked out with warnings.")

        TfvcEditFileProvider(project).checkoutFiles(listOf(file))

        assertThatLogContains("checkout ${file.toNioPath()}")
    }

    private fun createWorkspaceFile(name: String, text: String) =
        workspaceRoot.resolve(name).let { path ->
            Files.createDirectories(path.parent)
            Files.writeString(path, text)
            checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
        }

    private fun writeWorkspaceDiscoveryOutputs() {
        writeResponse(
            "workfold.stdout",
            """
            Collection: http://tfs:8080/tfs/DefaultCollection
            Workspace: WorkspaceOne;ian
            Workspace type: Local
            Server folder: ${'$'}/Sample
            Local folder: ${workspaceRoot.toString()}
            """.trimIndent(),
        )
        writeResponse(
            "workspaces.stdout",
            """
            <Workspaces>
              <Workspace name="WorkspaceOne" owner="ian" computer="DEVBOX" location="Local">
                <Collection>http://tfs:8080/tfs/DefaultCollection</Collection>
                <WorkingFolder localItem="${workspaceRoot}" serverItem="${'$'}/Sample" />
              </Workspace>
            </Workspaces>
            """.trimIndent(),
        )
    }

    private fun writeResponse(name: String, content: String) {
        Files.writeString(responsesDir.resolve(name), content)
    }

    private fun logText(): String = if (Files.exists(logFile)) Files.readString(logFile) else ""

    private fun assertThatLogContains(expected: String) {
        assertTrue("Expected log to contain '$expected' but was:\n${logText()}", logText().contains(expected))
    }

    private fun fakeTfScript(): String =
        """
        #!/bin/sh
        set -eu
        LOG_FILE='${logFile}'
        RESP_DIR='${responsesDir}'
        CMD="${'$'}1"
        shift || true
        printf '%s %s\n' "${'$'}CMD" "${'$'}*" >> "${'$'}LOG_FILE"
        STDOUT_FILE="${'$'}RESP_DIR/${'$'}CMD.stdout"
        STDERR_FILE="${'$'}RESP_DIR/${'$'}CMD.stderr"
        EXIT_FILE="${'$'}RESP_DIR/${'$'}CMD.exit"
        if [ -f "${'$'}STDOUT_FILE" ]; then cat "${'$'}STDOUT_FILE"; fi
        if [ -f "${'$'}STDERR_FILE" ]; then cat "${'$'}STDERR_FILE" >&2; fi
        if [ -f "${'$'}EXIT_FILE" ]; then exit "$(cat "${'$'}EXIT_FILE")"; fi
        exit 0
        """.trimIndent()
}
