package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.parsing.TfShelvesetsParser
import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TfvcIntegrationTest : BasePlatformTestCase() {
    private lateinit var fakeTfDir: Path
    private lateinit var responsesDir: Path
    private lateinit var logFile: Path
    private lateinit var workspaceRoot: Path
    private val workspaceMappings = linkedMapOf<Path, String>()

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
        registerWorkspace(workspaceRoot, "${'$'}/Sample")
    }

    fun `test workspace discovery resolves mapped root`() {
        val discovered = TfvcWorkspaceService.getInstance(project).findWorkspaceFor(workspaceRoot)

        assertNotNull(discovered)
        assertEquals(workspaceRoot.normalize(), discovered!!.localRoot)
        assertEquals("$/Sample", discovered.serverPath)
    }

    fun `test tfvc vcs exposes checkin environment`() {
        assertNotNull(TfvcVcs(project).checkinEnvironment)
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

    fun `test scheduling unversioned files for addition runs tf add`() {
        val file = createWorkspaceFile("unversioned.txt", "content")
        writeResponse("add.stdout", "")

        val errors = checkNotNull(checkinEnvironment().scheduleUnversionedFilesForAddition(listOf(file)))

        assertThat(errors).isEmpty()
        assertThatLogContains("add ${file.toNioPath()}")
    }

    fun `test scheduling missing file for deletion runs tf delete`() {
        val deletedPath = workspaceRoot.resolve("deleted.txt")
        Files.writeString(deletedPath, "content")
        Files.delete(deletedPath)
        writeResponse("delete.stdout", "")

        val errors = checkNotNull(checkinEnvironment().scheduleMissingFileForDeletion(
            listOf(LocalFilePath(deletedPath, false)),
        ))

        assertThat(errors).isEmpty()
        assertThatLogContains("delete $deletedPath")
    }

    fun `test committing a change runs tf checkin with comment`() {
        val file = createWorkspaceFile("commit.txt", "content")
        writeResponse("checkin.stdout", "")

        val feedback = linkedSetOf<String>()
        val errors = checkNotNull(checkinEnvironment().commit(
            listOf(buildAddedChange(file)),
            "  ship it  ",
            CommitContext(),
            feedback,
        ))

        assertThat(errors).isEmpty()
        assertThatLogContains("checkin ${file.toNioPath()} /comment:ship it /noprompt")
        assertThat(feedback).contains("TFVC check-in succeeded for $workspaceRoot")
    }

    fun `test multi root commit runs one tf checkin per root`() {
        val secondRoot = createWorkspaceRoot("tfvc-workspace-two")
        registerWorkspace(secondRoot, "${'$'}/SampleTwo")
        val firstFile = createWorkspaceFile("root-one.txt", "content")
        val secondFile = createWorkspaceFile(secondRoot, "root-two.txt", "content")
        writeResponse("checkin.stdout", "")

        val errors = checkNotNull(checkinEnvironment().commit(
            listOf(buildAddedChange(firstFile), buildAddedChange(secondFile)),
            "multi root",
            CommitContext(),
            linkedSetOf(),
        ))

        assertThat(errors).isEmpty()
        val checkinLines = logText().lineSequence().filter { it.startsWith("checkin ") }.toList()
        assertThat(checkinLines).hasSize(2)
        assertThat(checkinLines.joinToString("\n")).contains(firstFile.toNioPath().toString(), secondFile.toNioPath().toString())
    }

    fun `test partial root failure returns exceptions but still attempts other roots`() {
        val secondRoot = createWorkspaceRoot("tfvc-workspace-failing")
        registerWorkspace(secondRoot, "${'$'}/SampleFailing")
        val firstFile = createWorkspaceFile("root-one-ok.txt", "content")
        val secondFile = createWorkspaceFile(secondRoot, "root-two-fails.txt", "content")
        writeResponse("checkin.stdout", "")
        writeWorkingDirectoryResponse("checkin", secondRoot, "stderr", "root two failed")
        writeWorkingDirectoryResponse("checkin", secondRoot, "exit", "2")

        val errors = checkNotNull(checkinEnvironment().commit(
            listOf(buildAddedChange(secondFile), buildAddedChange(firstFile)),
            "multi root",
            CommitContext(),
            linkedSetOf(),
        ))

        assertThat(errors).hasSize(1)
        assertThat(errors.single().message).contains("root two failed")
        val checkinLines = logText().lineSequence().filter { it.startsWith("checkin ") }.toList()
        assertThat(checkinLines).hasSize(2)
        assertThat(checkinLines.joinToString("\n")).contains(secondFile.toNioPath().toString(), firstFile.toNioPath().toString())
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

    private fun createWorkspaceRoot(prefix: String): Path {
        val root = Files.createTempDirectory(prefix)
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            root.toString(),
            root.toRealPath().toString(),
        )
        return root
    }

    private fun createWorkspaceFile(root: Path, name: String, text: String) =
        root.resolve(name).let { path ->
            Files.createDirectories(path.parent)
            Files.writeString(path, text)
            checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
        }

    private fun registerWorkspace(root: Path, serverPath: String) {
        workspaceMappings[root.normalize()] = serverPath
        writeArgumentResponse(
            "workfold",
            root,
            "stdout",
            """
            Collection: http://tfs:8080/tfs/DefaultCollection
            Workspace: ${workspaceNameFor(root)};ian
            Workspace type: Local
            Server folder: $serverPath
            Local folder: ${root.toString()}
            """.trimIndent(),
        )
        writeResponse(
            "workspaces.stdout",
            """
            <Workspaces>
            ${workspaceMappings.entries.joinToString("\n") { (localRoot, mappedServerPath) ->
                """
                  <Workspace name="${workspaceNameFor(localRoot)}" owner="ian" computer="DEVBOX" location="Local">
                    <Collection>http://tfs:8080/tfs/DefaultCollection</Collection>
                    <WorkingFolder localItem="$localRoot" serverItem="$mappedServerPath" />
                  </Workspace>
                """.trimIndent()
            }}
            </Workspaces>
            """.trimIndent(),
        )
    }

    private fun writeResponse(name: String, content: String) {
        Files.writeString(responsesDir.resolve(name), content)
    }

    private fun writeArgumentResponse(command: String, argument: Path, stream: String, content: String) {
        writeResponse("$command.arg.${sanitizeResponseKey(argument.toString())}.$stream", content)
    }

    private fun writeWorkingDirectoryResponse(command: String, workingDirectory: Path, stream: String, content: String) {
        writeResponse("$command.wd.${sanitizeResponseKey(workingDirectory.toString())}.$stream", content)
    }

    private fun logText(): String = if (Files.exists(logFile)) Files.readString(logFile) else ""

    private fun assertThatLogContains(expected: String) {
        assertTrue("Expected log to contain '$expected' but was:\n${logText()}", logText().contains(expected))
    }

    private fun buildAddedChange(file: com.intellij.openapi.vfs.VirtualFile): Change {
        val filePath = LocalFilePath(file.toNioPath(), file.isDirectory)
        return Change(null, CurrentContentRevision.create(filePath), FileStatus.ADDED)
    }

    private fun checkinEnvironment() = checkNotNull(TfvcVcs(project).checkinEnvironment)

    private fun sanitizeResponseKey(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun workspaceNameFor(root: Path): String = "Workspace${sanitizeResponseKey(root.fileName.toString())}"

    private fun fakeTfScript(): String =
        """
        #!/bin/sh
        set -eu
        LOG_FILE='${logFile}'
        RESP_DIR='${responsesDir}'
        sanitize() {
          printf '%s' "${'$'}1" | tr -c 'A-Za-z0-9._-' '_'
        }
        resolve_response() {
          STREAM="${'$'}1"
          ARGUMENT="${'$'}{2-}"
          if [ -n "${'$'}ARGUMENT" ]; then
            ARG_FILE="${'$'}RESP_DIR/${'$'}CMD.arg.$(sanitize "${'$'}ARGUMENT").${'$'}STREAM"
            if [ -f "${'$'}ARG_FILE" ]; then
              printf '%s' "${'$'}ARG_FILE"
              return 0
            fi
          fi
          WD_FILE="${'$'}RESP_DIR/${'$'}CMD.wd.$(sanitize "$(pwd)").${'$'}STREAM"
          if [ -f "${'$'}WD_FILE" ]; then
            printf '%s' "${'$'}WD_FILE"
            return 0
          fi
          DEFAULT_FILE="${'$'}RESP_DIR/${'$'}CMD.${'$'}STREAM"
          if [ -f "${'$'}DEFAULT_FILE" ]; then
            printf '%s' "${'$'}DEFAULT_FILE"
            return 0
          fi
          return 1
        }
        CMD="${'$'}1"
        shift || true
        FIRST_ARG="${'$'}{1-}"
        printf '%s %s\n' "${'$'}CMD" "${'$'}*" >> "${'$'}LOG_FILE"
        if STDOUT_FILE="$(resolve_response stdout "${'$'}FIRST_ARG")"; then cat "${'$'}STDOUT_FILE"; fi
        if STDERR_FILE="$(resolve_response stderr "${'$'}FIRST_ARG")"; then cat "${'$'}STDERR_FILE" >&2; fi
        if EXIT_FILE="$(resolve_response exit "${'$'}FIRST_ARG")"; then exit "$(cat "${'$'}EXIT_FILE")"; fi
        exit 0
        """.trimIndent()
}
