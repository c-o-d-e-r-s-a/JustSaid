package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TESTING.md C.1: the model's line format parses into items, `[Qty]` is optional,
 * `NONE` yields an empty list, and anything off-format is dropped rather than
 * guessed (Constitution G1/G2 — the model is untrusted input).
 */
class PromiseParserTest {

    @Test
    fun `well-formed line with quantity parses`() {
        val items = PromiseParser.parse("""Buy milk [2 gallons] (Proof: "I'll pick up two gallons of milk")""")

        assertThat(items).hasSize(1)
        with(items.first()) {
            assertThat(task).isEqualTo("Buy milk")
            assertThat(quantity).isEqualTo("2 gallons")
            assertThat(proofQuote).isEqualTo("I'll pick up two gallons of milk")
            assertThat(markedUnconfirmed).isFalse()
        }
    }

    @Test
    fun `quantity is optional`() {
        val items = PromiseParser.parse("""Call the plumber (Proof: "I will call the plumber tomorrow")""")

        assertThat(items).hasSize(1)
        assertThat(items.first().quantity).isNull()
        assertThat(items.first().task).isEqualTo("Call the plumber")
    }

    @Test
    fun `NONE token yields empty list`() {
        assertThat(PromiseParser.parse("NONE")).isEmpty()
        assertThat(PromiseParser.parse("none")).isEmpty()
        assertThat(PromiseParser.parse("  NONE  ")).isEmpty()
    }

    @Test
    fun `blank output yields empty list`() {
        assertThat(PromiseParser.parse("")).isEmpty()
        assertThat(PromiseParser.parse("   \n  ")).isEmpty()
    }

    @Test
    fun `unconfirmed prefix is captured`() {
        val items = PromiseParser.parse("""Unconfirmed: Send the report (Proof: "someone will send the report")""")

        assertThat(items).hasSize(1)
        assertThat(items.first().markedUnconfirmed).isTrue()
        assertThat(items.first().task).isEqualTo("Send the report")
    }

    @Test
    fun `multiple lines parse independently and junk is dropped`() {
        val raw = """
            Here are the tasks I found:
            Buy bread (Proof: "I'll grab a loaf of bread")
            Fix the fence [3 boards] (Proof: "three boards should do it")
            This line has no proof and must be dropped
            Thanks for listening!
        """.trimIndent()

        val items = PromiseParser.parse(raw)

        assertThat(items).hasSize(2)
        assertThat(items[0].task).isEqualTo("Buy bread")
        assertThat(items[1].quantity).isEqualTo("3 boards")
    }

    @Test
    fun `line without a quoted proof is dropped`() {
        assertThat(PromiseParser.parse("Buy bread (Proof: missing quotes)")).isEmpty()
    }

    @Test
    fun `bulleted lines parse`() {
        val items = PromiseParser.parse("""- Water the plants (Proof: "I'll water the plants Sunday")""")

        assertThat(items).hasSize(1)
        assertThat(items.first().task).isEqualTo("Water the plants")
    }
}
