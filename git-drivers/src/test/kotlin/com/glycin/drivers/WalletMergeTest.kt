package com.glycin.drivers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WalletMergeTest {

    @Test
    fun `each resource takes the larger side`() {
        val ours = listOf("silicon 300", "copper 0", "gold 5", "tokens 50")
        val theirs = listOf("silicon 50", "copper 0", "gold 500", "tokens 50")
        assertEquals(
            listOf("silicon 300", "copper 0", "gold 500", "tokens 50"),
            WalletMerge.merge(ours, theirs),
        )
    }

    @Test
    fun `resources only theirs knows are appended after ours`() {
        assertEquals(
            listOf("gold 1", "silicon 9"),
            WalletMerge.merge(listOf("gold 1"), listOf("silicon 9")),
        )
    }

    @Test
    fun `blank and malformed lines are ignored`() {
        assertEquals(
            listOf("gold 7", "junk 0"),
            WalletMerge.merge(listOf("", "gold 7"), listOf("junk", "   ")),
        )
    }
}
