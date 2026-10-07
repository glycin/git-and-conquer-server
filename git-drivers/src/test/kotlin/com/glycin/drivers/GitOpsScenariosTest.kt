package com.glycin.drivers

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Runs the drivers on the JVM through real git. The native binary is the same code.
class GitOpsScenariosTest {

    @TempDir
    lateinit var repo: Path

    private lateinit var mergeSha: String

    @BeforeEach
    fun initRepo() {
        assumeTrue(runCatching { ProcessBuilder("git", "--version").start().waitFor() == 0 }.getOrDefault(false))
        val driver = driverCommand()
        git("init", "-b", "main", ".")
        git("config", "user.name", "scenario")
        git("config", "user.email", "scenario@local")
        git("config", "core.autocrlf", "false")
        git("config", "merge.mapmerge.driver", "$driver mapmerge %O %A %B")
        git("config", "merge.walletmerge.driver", "$driver walletmerge %O %A %B")
        git("config", "diff.mapdiff.command", "$driver mapdiff")
        write(".gitattributes", "map.grid merge=mapmerge\nmap.grid diff=mapdiff\nwallet.res merge=walletmerge\n")
        write("map.grid", List(8) { "........" }.joinToString("\n", postfix = "\n"))
        writeWallet(silicon = 100, gold = 10)
        git("add", "-A")
        git("commit", "-m", "Initial world")
    }

    @Test
    fun `fast-forward merge`() {
        git("checkout", "-b", "ff")
        setCell(0, 0, 'b'); git("commit", "-am", "ff: barracks")
        git("checkout", "main")
        git("merge", "--ff", "ff")
        assertEquals(rev("ff"), rev("HEAD"))
        assertEquals(1, parents("HEAD"))
        assertEquals('b', cell(0, 0))
    }

    @Test
    fun `merge commit takes both sides and the wallet max`() {
        divergeWithWallets()
        git("merge", "--no-edit", "--ff", "side")
        assertEquals(2, parents("HEAD"))
        assertEquals('r', cell(2, 2))
        assertEquals('t', cell(1, 1))
        assertEquals(300, wallet("silicon"))
        assertEquals(500, wallet("gold"))
    }

    @Test
    fun `ff-only refuses a diverged branch and leaves nothing in progress`() {
        divergeWithWallets()
        git("checkout", "-b", "diverged", "main~1")
        setCell(3, 3, 'b'); git("commit", "-am", "diverged: barracks")
        git("checkout", "main")
        assertEquals(128, gitExitCode("merge", "--ff-only", "diverged"))
        assertFalse(Files.exists(repo.resolve(".git/MERGE_HEAD")))
    }

    @Test
    fun `no-ff forces a merge commit`() {
        git("checkout", "-b", "linear")
        setCell(4, 4, 'r'); git("commit", "-am", "linear: rock")
        git("checkout", "main")
        git("merge", "--no-ff", "--no-edit", "linear")
        assertEquals(2, parents("HEAD"))
    }

    @Test
    fun `rebase stops on each conflicting step and finishes once resolved`() {
        git("checkout", "-b", "feat")
        setCell(5, 5, 'b'); git("commit", "-am", "feat: barracks A")
        setCell(6, 6, 'b'); git("commit", "-am", "feat: barracks B")
        git("checkout", "main")
        setCell(5, 5, 't'); setCell(6, 6, 't'); git("commit", "-am", "main: trees over both")
        git("checkout", "feat")

        assertEquals(1, gitExitCode("rebase", "main"))
        assertTrue(Files.isDirectory(repo.resolve(".git/rebase-merge")))
        assertEquals("refs/heads/feat", read(".git/rebase-merge/head-name").trim())
        assertEquals("HEAD", git("rev-parse", "--abbrev-ref", "HEAD"))
        assertEquals(".....<t|b>..", gridLine(5))
        assertEquals(".....t..", git("show", ":2:map.grid").lines()[5])
        assertEquals(".....b..", git("show", ":3:map.grid").lines()[5])

        resolveRow(5, 'b'); git("add", "map.grid")
        assertEquals(1, gitExitCode("-c", "core.editor=true", "rebase", "--continue"))
        assertEquals("map.grid", git("diff", "--name-only", "--diff-filter=U"))
        assertEquals("......<t|b>.", gridLine(6))

        resolveRow(6, 't'); git("add", "map.grid")
        assertEquals(0, gitExitCode("-c", "core.editor=true", "rebase", "--continue"))
        assertEquals("feat", git("rev-parse", "--abbrev-ref", "HEAD"))
        assertEquals('b', cell(5, 5))
        assertEquals('t', cell(6, 6))
        assertEquals(0, gitExitCode("merge-base", "--is-ancestor", "main", "feat"))
    }

    @Test
    fun `cherry-pick brings one building across`() {
        git("checkout", "-b", "spoils")
        setCell(7, 7, 'b'); git("commit", "-am", "spoils: barracks")
        git("checkout", "main")
        setCell(0, 7, 'r'); git("commit", "-am", "main: rock meanwhile")
        git("cherry-pick", "spoils")
        assertEquals('b', cell(7, 7))
        assertNotEquals(rev("spoils"), rev("HEAD"))
    }

    @Test
    fun `cherry-pick resolved to nothing needs --skip`() {
        git("checkout", "-b", "spoils")
        setCell(7, 7, 'b'); git("commit", "-am", "spoils: barracks")
        git("checkout", "-b", "empty", "main")
        setCell(7, 7, 't'); git("commit", "-am", "empty: tree where the pick goes")

        assertEquals(1, gitExitCode("cherry-pick", "spoils"))
        resolveRow(7, 't'); git("add", "map.grid")
        assertEquals(1, gitExitCode("-c", "core.editor=true", "cherry-pick", "--continue"))
        assertEquals(0, gitExitCode("diff", "--cached", "--quiet"))
        assertTrue(Files.exists(repo.resolve(".git/CHERRY_PICK_HEAD")))
        git("cherry-pick", "--skip")
        assertFalse(Files.exists(repo.resolve(".git/CHERRY_PICK_HEAD")))
    }

    @Test
    fun `merge commits need -m 1 and revert through the driver`() {
        divergeWithWallets()
        git("merge", "--no-edit", "--ff", "side")
        mergeSha = rev("HEAD")
        setCell(0, 0, 'b'); git("commit", "-am", "main: barracks after merge")
        git("revert", "--no-edit", "HEAD")

        assertEquals(128, gitExitCode("cherry-pick", mergeSha))
        assertEquals(128, gitExitCode("revert", "--no-edit", mergeSha))
        assertEquals(2, parents(mergeSha))
        assertEquals(0, gitExitCode("revert", "--no-edit", "-m", "1", mergeSha))
        assertEquals('.', cell(1, 1))
        assertEquals('r', cell(2, 2))
        assertEquals(5, wallet("gold"))
    }

    @Test
    fun `revert of a plain commit un-builds`() {
        setCell(3, 3, 'b'); git("commit", "-am", "main: barracks")
        git("revert", "--no-edit", "HEAD")
        assertEquals('.', cell(3, 3))
    }

    @Test
    fun `diff and log --ext-diff render the per-cell diff`() {
        setCell(3, 3, 'b'); git("commit", "-am", "main: barracks")
        val diff = git("diff", "HEAD~1", "HEAD", "--", "map.grid")
        assertTrue(diff.contains("== map.grid : 1 cell changed =="), diff)
        assertTrue(diff.contains("+ (row   3, col   3)  '.'  ->  'b'"), diff)
        val log = git("log", "-p", "--ext-diff", "-1", "--", "map.grid")
        assertTrue(log.contains("== map.grid : 1 cell changed =="), log)
    }

    private fun divergeWithWallets() {
        git("checkout", "-b", "side")
        setCell(1, 1, 't'); writeWallet(silicon = 50, gold = 500); git("commit", "-am", "side: tree + gold")
        git("checkout", "main")
        setCell(2, 2, 'r'); writeWallet(silicon = 300, gold = 5); git("commit", "-am", "main: rock + silicon")
    }

    private fun driverCommand(): String {
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val classpath = System.getProperty("java.class.path")
        return "\"$java\" -cp \"$classpath\" com.glycin.drivers.MainKt"
    }

    private fun git(vararg args: String): String {
        val process = start(*args)
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$out$err" }
        return out.trimEnd('\n')
    }

    private fun gitExitCode(vararg args: String): Int {
        val process = start(*args)
        process.inputStream.readAllBytes()
        process.errorStream.readAllBytes()
        return process.waitFor()
    }

    private fun start(vararg args: String): Process =
        ProcessBuilder("git", *args).directory(repo.toFile()).also { it.environment()["NO_COLOR"] = "1" }.start()

    private fun rev(ref: String) = git("rev-parse", ref)
    private fun parents(ref: String) = git("rev-list", "--parents", "-n", "1", ref).split(' ').size - 1

    private fun read(rel: String) = Files.readString(repo.resolve(rel))
    private fun write(rel: String, content: String) { Files.writeString(repo.resolve(rel), content) }
    private fun writeWallet(silicon: Int, gold: Int) = write("wallet.res", "silicon $silicon\ncopper 0\ngold $gold\ntokens 50\n")

    private fun gridLines() = read("map.grid").trimEnd('\n').split('\n')
    private fun gridLine(row: Int) = gridLines()[row]
    private fun cell(row: Int, col: Int) = gridLine(row)[col]
    private fun setCell(row: Int, col: Int, char: Char) {
        val lines = gridLines().toMutableList()
        lines[row] = lines[row].substring(0, col) + char + lines[row].substring(col + 1)
        write("map.grid", lines.joinToString("\n", postfix = "\n"))
    }
    private fun resolveRow(row: Int, char: Char) {
        val lines = gridLines().toMutableList()
        lines[row] = lines[row].replace(Regex("<.\\|.>"), char.toString())
        write("map.grid", lines.joinToString("\n", postfix = "\n"))
    }
    private fun wallet(key: String) = read("wallet.res").lines().first { it.startsWith("$key ") }.substringAfter(' ').toInt()
}
