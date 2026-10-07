package com.glycin.drivers

// Each `<resource> <amount>` line keeps the bigger amount. Never conflicts.
object WalletMerge {

    fun merge(ours: List<String>, theirs: List<String>): List<String> {
        val amounts = LinkedHashMap<String, Long>()
        for (line in ours) {
            val (resource, amount) = parseLine(line) ?: continue
            amounts.putIfAbsent(resource, amount)
        }
        for (line in theirs) {
            val (resource, amount) = parseLine(line) ?: continue
            amounts.merge(resource, amount, ::maxOf)
        }
        return amounts.map { (resource, amount) -> "$resource $amount" }
    }

    private fun parseLine(line: String): Pair<String, Long>? {
        val fields = line.trim().split(WHITESPACE)
        val resource = fields.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
        return resource to (fields.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    private val WHITESPACE = Regex("\\s+")
}
