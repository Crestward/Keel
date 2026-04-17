// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 14 pure JVM tests for [AmountParser.parseToKobo]. */
class AmountParserTest {

    // ─── Valid inputs ─────────────────────────────────────────────────────────

    @Test fun ngn_with_comma_decimal() =
        assertEquals(500_000L, AmountParser.parseToKobo("NGN5,000.00"))

    @Test fun ngn_space_with_comma_decimal() =
        assertEquals(500_000L, AmountParser.parseToKobo("NGN 5,000.00"))

    @Test fun naira_sign() =
        assertEquals(1_500_000L, AmountParser.parseToKobo("₦15,000"))

    @Test fun capital_n_prefix() =
        assertEquals(50_050L, AmountParser.parseToKobo("N500.50"))

    @Test fun no_prefix_decimal() =
        assertEquals(200_000L, AmountParser.parseToKobo("2,000.00"))

    @Test fun whole_naira_only() =
        assertEquals(50_000L, AmountParser.parseToKobo("500"))

    @Test fun single_decimal_padded() =
        assertEquals(50_050L, AmountParser.parseToKobo("500.5"))

    @Test fun zero_kobo_part() =
        assertEquals(100_000L, AmountParser.parseToKobo("NGN1,000.00"))

    @Test fun large_amount() =
        assertEquals(1_000_000_000L, AmountParser.parseToKobo("NGN10,000,000.00"))

    @Test fun lowercase_ngn_prefix() =
        assertEquals(100_000L, AmountParser.parseToKobo("ngn1,000.00"))

    @Test fun whitespace_trimmed() =
        assertEquals(50_000L, AmountParser.parseToKobo("  NGN 500.00  "))

    // ─── Invalid / garbage inputs ─────────────────────────────────────────────

    @Test fun blank_returns_null() =
        assertNull(AmountParser.parseToKobo(""))

    @Test fun non_numeric_returns_null() =
        assertNull(AmountParser.parseToKobo("NGNabc"))

    @Test fun three_decimal_places_returns_null() =
        assertNull(AmountParser.parseToKobo("500.123"))
}
