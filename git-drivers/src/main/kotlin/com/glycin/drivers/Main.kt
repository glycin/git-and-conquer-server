package com.glycin.drivers

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

// git-drivers mapmerge    %O %A %B
// git-drivers walletmerge %O %A %B
// git-drivers mapdiff     <git's 7 GIT_EXTERNAL_DIFF args>
// Merge drivers overwrite %A and exit 0 (clean), 1 (conflicts) or 2 (error, %A untouched).

private const val EXIT_CLEAN = 0
private const val EXIT_CONFLICTS = 1
private const val EXIT_ERROR = 2

fun main(args: Array<String>) {
    val exitCode = try {
        when (args.getOrNull(0)) {
            "mapmerge" -> runMapMerge(args)
            "walletmerge" -> runWalletMerge(args)
            "mapdiff" -> runMapDiff(args)
            else -> printUsage()
        }
    } catch (e: Exception) {
        System.err.println("git-drivers: ${e.message ?: e::class.simpleName}")
        EXIT_ERROR
    }
    exitProcess(exitCode)
}

private fun runMapMerge(args: Array<String>): Int {
    val (base, ours, theirs) = mergeInputs(args)
    val result = MapMerge.merge(readLines(base), readLines(ours), readLines(theirs))
    writeAtomically(ours, result.lines)
    return if (result.clean) EXIT_CLEAN else EXIT_CONFLICTS
}

private fun runWalletMerge(args: Array<String>): Int {
    val (_, ours, theirs) = mergeInputs(args)
    writeAtomically(ours, WalletMerge.merge(readLines(ours), readLines(theirs)))
    return EXIT_CLEAN
}

private fun runMapDiff(args: Array<String>): Int {
    require(args.size >= 6) { "mapdiff expects git's external diff arguments" }
    val path = args[1]
    val oldFile = Paths.get(args[2])
    val newFile = Paths.get(args[5])
    val color = System.getenv("NO_COLOR") == null
    print(MapDiff.render(readLines(oldFile), readLines(newFile), path, color))
    System.out.flush()
    return EXIT_CLEAN
}

private fun printUsage(): Int {
    System.err.println("usage: git-drivers (mapmerge|walletmerge) <base> <ours> <theirs> | mapdiff <git diff args>")
    return EXIT_ERROR
}

private fun mergeInputs(args: Array<String>): Triple<Path, Path, Path> {
    require(args.size >= 4) { "${args[0]} expects <base> <ours> <theirs>" }
    return Triple(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]))
}

private fun readLines(path: Path): List<String> =
    if (Files.exists(path)) Files.readAllLines(path) else emptyList()

private fun writeAtomically(target: Path, lines: List<String>) {
    val tmp = target.resolveSibling(target.fileName.toString() + ".merge_tmp")
    Files.writeString(tmp, lines.joinToString("\n", postfix = "\n"))
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
}
