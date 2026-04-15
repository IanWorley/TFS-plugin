package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.TfvcPathMapper
import com.ianworley.tfvc.tf.WorkspaceContext
import com.ianworley.tfvc.tf.PendingChangeType
import com.ianworley.tfvc.tf.WorkspaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class TfStatusParserTest {
    private val workspace = WorkspaceContext(
        localRoot = Path.of("build", "tmp", "tfvc-status-parser").toAbsolutePath().normalize(),
        serverPath = "$/Sample",
        workspaceName = "WorkspaceOne",
        owner = "ian",
        collectionUrl = "http://tfs:8080/tfs/DefaultCollection",
        workspaceType = WorkspaceType.SERVER,
    )

    @Test
    fun `parses detailed status output including candidates`() {
        val parsed = TfStatusParser.parse(FixtureLoader.read("status.txt"))

        assertThat(parsed).hasSize(3)
        assertThat(parsed[0].changeType).isEqualTo(PendingChangeType.EDIT)
        assertThat(parsed[0].localPath).isEqualTo(Paths.get("C:\\src\\sample\\file.txt"))
        assertThat(parsed[1].changeType).isEqualTo(PendingChangeType.ADD)
        assertThat(parsed[1].isCandidate).isTrue()
        assertThat(parsed[2].changeType).isEqualTo(PendingChangeType.RENAME)
        assertThat(parsed[2].locked).isTrue()
    }

    @Test
    fun `parses server item only output through workspace mapping`() {
        val parsed = TfStatusParser.parse(
            FixtureLoader.read("status-server.txt"),
            resolveServerItem = { serverItem -> TfvcPathMapper.toLocalPath(workspace, serverItem) },
        )

        assertThat(parsed).hasSize(3)
        assertThat(parsed[0].localPath).isEqualTo(workspace.localRoot.resolve("file.txt").normalize())
        assertThat(parsed[0].changeType).isEqualTo(PendingChangeType.EDIT)
        assertThat(parsed[1].localPath).isEqualTo(workspace.localRoot.resolve("newfile.txt").normalize())
        assertThat(parsed[1].changeType).isEqualTo(PendingChangeType.ADD)
        assertThat(parsed[1].isCandidate).isTrue()
        assertThat(parsed[2].localPath).isEqualTo(workspace.localRoot.resolve("renamed.txt").normalize())
        assertThat(parsed[2].changeType).isEqualTo(PendingChangeType.RENAME)
        assertThat(parsed[2].locked).isTrue()
    }

    @Test
    fun `skips unresolved server items`() {
        val parsed = TfStatusParser.parse(
            """
            Server item: $/Other/file.txt
            Change: edit
            User: ian
            Lock: none
            """.trimIndent(),
            resolveServerItem = { serverItem -> TfvcPathMapper.toLocalPath(workspace, serverItem) },
        )

        assertThat(parsed).isEmpty()
    }
}
