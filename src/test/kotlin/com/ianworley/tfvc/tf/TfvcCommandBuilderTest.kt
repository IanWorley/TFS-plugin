package com.ianworley.tfvc.tf

import com.intellij.openapi.vcs.LocalFilePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class TfvcCommandBuilderTest {
    @Test
    fun `builds status arguments for server paths`() {
        val args = TfvcCommandBuilder.status("$/Sample")

        assertThat(args).containsExactly("status", "$/Sample", "/recursive", "/format:detailed")
    }

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
    fun `builds checkin arguments with comment`() {
        val args = TfvcCommandBuilder.checkin(
            listOf(LocalFilePath("repo/file.txt", false)),
            "  Hello world  ",
        )

        assertThat(args).containsExactly("checkin", "repo/file.txt", "/comment:Hello world")
    }

    @Test
    fun `builds checkin arguments without comment`() {
        val args = TfvcCommandBuilder.checkin(
            listOf(LocalFilePath("repo/file.txt", false)),
            "   ",
        )

        assertThat(args).containsExactly("checkin", "repo/file.txt")
    }

    @Test
    fun `builds checkin arguments with recursive for directories`() {
        val args = TfvcCommandBuilder.checkin(
            listOf(
                LocalFilePath("repo/folder", true),
                LocalFilePath("repo/folder/file.txt", false),
            ),
            null,
        )

        assertThat(args).containsExactly("checkin", "repo/folder", "repo/folder/file.txt", "/recursive")
    }

    @Test
    fun `builds delete arguments with file paths`() {
        val args = TfvcCommandBuilder.delete(
            listOf(
                LocalFilePath("repo/one.txt", false),
                LocalFilePath("repo/two.txt", false),
            ),
        )

        assertThat(args).containsExactly("delete", "repo/one.txt", "repo/two.txt")
    }

    @Test
    fun `builds delete arguments with recursive for directories`() {
        val args = TfvcCommandBuilder.delete(
            listOf(
                LocalFilePath("repo/folder", true),
                LocalFilePath("repo/folder/file.txt", false),
            ),
        )

        assertThat(args).containsExactly("delete", "repo/folder", "repo/folder/file.txt", "/recursive")
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
