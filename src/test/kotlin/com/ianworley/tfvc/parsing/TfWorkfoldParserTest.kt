package com.ianworley.tfvc.parsing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class TfWorkfoldParserTest {
    @Test
    fun `parses english workfold output`() {
        val parsed = TfWorkfoldParser.parse(FixtureLoader.read("workfold.txt"))

        assertThat(parsed).isNotNull
        assertThat(parsed!!.workspaceName).isEqualTo("WorkspaceOne")
        assertThat(parsed.owner).isEqualTo("ian")
        assertThat(parsed.collectionUrl).isEqualTo("http://tfs:8080/tfs/DefaultCollection")
        assertThat(parsed.serverPath).isEqualTo("$/Sample")
        assertThat(parsed.localRoot).isEqualTo(Paths.get("C:\\src\\sample"))
    }
}
