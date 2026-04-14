package com.ianworley.tfvc.parsing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TfShelvesetsParserTest {
    @Test
    fun `parses brief shelveset listing`() {
        val parsed = TfShelvesetsParser.parse(FixtureLoader.read("shelvesets.txt"))

        assertThat(parsed).hasSize(2)
        assertThat(parsed[0].name).isEqualTo("shelf-one")
        assertThat(parsed[0].owner).isEqualTo("ian")
        assertThat(parsed[0].comment).isEqualTo("First shelveset")
    }
}
