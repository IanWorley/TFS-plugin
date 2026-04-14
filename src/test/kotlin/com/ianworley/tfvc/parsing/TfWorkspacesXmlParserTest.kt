package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.WorkspaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class TfWorkspacesXmlParserTest {
    @Test
    fun `parses workspace metadata xml`() {
        val parsed = TfWorkspacesXmlParser.parse(FixtureLoader.read("workspaces.xml"))

        assertThat(parsed).hasSize(1)
        assertThat(parsed.single().workspaceName).isEqualTo("WorkspaceOne")
        assertThat(parsed.single().owner).isEqualTo("ian")
        assertThat(parsed.single().serverPath).isEqualTo("$/Sample")
        assertThat(parsed.single().localRoot).isEqualTo(Paths.get("C:\\src\\sample"))
        assertThat(parsed.single().workspaceType).isEqualTo(WorkspaceType.LOCAL)
    }
}
