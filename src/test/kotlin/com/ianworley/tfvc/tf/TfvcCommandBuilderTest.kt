package com.ianworley.tfvc.tf

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class TfvcCommandBuilderTest {
    @Test
    fun `builds add arguments with recursive for directories`() {
        val tempDir = Files.createTempDirectory("tfvc-add")
        val file = tempDir.resolve("file.txt")
        Files.writeString(file, "x")
        val args = TfvcCommandBuilder.add(listOf(tempDir, file))

        assertThat(args).containsExactly("add", tempDir.toString(), file.toString(), "/recursive")
    }

    @Test
    fun `builds checkout arguments`() {
        val args = TfvcCommandBuilder.checkout(listOf(Path.of("repo/file.txt")))

        assertThat(args).containsExactly("checkout", "repo/file.txt")
    }

    @Test
    fun `builds shelve arguments`() {
        val args = TfvcCommandBuilder.shelve(
            name = "shelf-one",
            paths = listOf(Path.of("repo/file.txt")),
            comment = "Hello",
            replaceExisting = true,
            moveChanges = true,
        )

        assertThat(args).containsExactly("shelve", "/replace", "/move", "/comment:Hello", "shelf-one", "repo/file.txt")
    }

    @Test
    fun `builds unshelve arguments`() {
        val args = TfvcCommandBuilder.unshelve("shelf-one", "ian", true)

        assertThat(args).containsExactly("unshelve", "/move", "shelf-one;ian")
    }
}
