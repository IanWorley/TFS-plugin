package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.PendingChangeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class TfStatusParserTest {
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
}
