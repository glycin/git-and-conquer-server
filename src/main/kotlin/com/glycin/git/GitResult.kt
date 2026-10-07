package com.glycin.git

data class GitResult(
    val exit: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exit == 0
    fun stdoutTrimmed(): String = stdout.trim()
}

fun GitResult.errorMessage(): String = stderr.trim().ifBlank { stdout.trim() }
