package com.glycin.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellLineTest {

    @Test
    fun `plain arguments render unquoted`() {
        assertEquals("git add -- objects/r0050_c0030.json", ShellLine.render(listOf("git", "add", "--", "objects/r0050_c0030.json")))
    }

    @Test
    fun `arguments with spaces are single-quoted`() {
        assertEquals(
            "git commit -m 'Battlefield update: 2 built, 1 destroyed'",
            ShellLine.render(listOf("git", "commit", "-m", "Battlefield update: 2 built, 1 destroyed")),
        )
    }

    @Test
    fun `arguments with single quotes fall back to double quotes`() {
        assertEquals("git commit -m \"it's alive\"", ShellLine.render(listOf("git", "commit", "-m", "it's alive")))
    }

    @Test
    fun `subcommand skips global options`() {
        assertEquals("cherry-pick", ShellLine.subcommand(listOf("-c", "core.editor=true", "cherry-pick", "--continue")))
        assertEquals("status", ShellLine.subcommand(listOf("--no-pager", "status")))
    }

    @Test
    fun `only repo-changing commands are echoed`() {
        assertTrue(ShellLine.mutates(listOf("commit", "-m", "x")))
        assertTrue(ShellLine.mutates(listOf("-c", "core.editor=true", "rebase", "--continue")))
        assertTrue(ShellLine.mutates(listOf("hash-object", "-w", "--stdin")))
        assertTrue(ShellLine.mutates(listOf("gc")))
        assertFalse(ShellLine.mutates(listOf("count-objects", "-v")))
        assertFalse(ShellLine.mutates(listOf("status", "--porcelain")))
        assertFalse(ShellLine.mutates(listOf("show", ":map.grid")))
        assertFalse(ShellLine.mutates(listOf("rev-parse", "HEAD")))
        assertFalse(ShellLine.mutates(listOf("diff", "--cached", "--name-status")))
    }
}
