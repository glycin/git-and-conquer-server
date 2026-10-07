package com.glycin.git

// Only commands that change the repo get echoed. The read-only ones would flood the chat.
object ShellLine {

    private val MUTATING_SUBCOMMANDS = setOf(
        "init", "config", "add", "read-tree", "rm", "reset", "restore", "commit", "checkout", "switch",
        "branch", "tag", "merge", "rebase", "cherry-pick", "revert",
        "hash-object", "update-index", "stash", "gc",
    )

    private val SAFE_ARG = Regex("^[A-Za-z0-9_@%+=:,./-][A-Za-z0-9_@%+=:,./^~-]*$")

    fun render(command: List<String>, env: Map<String, String> = emptyMap()): String =
        (env.map { (key, value) -> key + "=" + quote(value) } + command.map { quote(it) }).joinToString(" ")

    fun mutates(args: List<String>): Boolean = subcommand(args) in MUTATING_SUBCOMMANDS

    // First argument that isn't a global option like `-c key=val` or `--no-pager`.
    fun subcommand(args: List<String>): String? {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-c" || arg == "-C" -> i += 2
                arg.startsWith("-") -> i += 1
                else -> return arg
            }
        }
        return null
    }

    private fun quote(arg: String): String = when {
        arg.isEmpty() -> "''"
        SAFE_ARG.matches(arg) -> arg
        !arg.contains('\'') -> "'" + arg + "'"
        else -> "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`") + "\""
    }
}
