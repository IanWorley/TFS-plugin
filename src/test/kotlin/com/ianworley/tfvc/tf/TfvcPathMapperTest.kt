package com.ianworley.tfvc.tf

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path

class TfvcPathMapperTest {
    private val workspace = WorkspaceContext(
        localRoot = Path.of("build", "tmp", "tfvc-path-mapper").toAbsolutePath().normalize(),
        serverPath = "$/Sample",
        workspaceName = "WorkspaceOne",
        owner = "ian",
        collectionUrl = "http://tfs:8080/tfs/DefaultCollection",
        workspaceType = WorkspaceType.SERVER,
    )

    @Test
    fun `maps root local path to root server path`() {
        val mapped = TfvcPathMapper.toServerItem(workspace, workspace.localRoot)

        assertThat(mapped).isEqualTo("$/Sample")
    }

    @Test
    fun `maps nested local file to nested server item`() {
        val mapped = TfvcPathMapper.toServerItem(workspace, workspace.localRoot.resolve("src/file.txt"))

        assertThat(mapped).isEqualTo("$/Sample/src/file.txt")
    }

    @Test
    fun `maps server item back to local path`() {
        val mapped = TfvcPathMapper.toLocalPath(workspace, "$/sample/src/file.txt")

        assertThat(mapped).isEqualTo(workspace.localRoot.resolve("src/file.txt").normalize())
    }

    @Test
    fun `returns null for out of scope server items`() {
        val mapped = TfvcPathMapper.toLocalPath(workspace, "$/Other/file.txt")

        assertThat(mapped).isNull()
    }
}
