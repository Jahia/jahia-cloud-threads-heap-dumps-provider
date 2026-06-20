package org.jahia.community.external.cloud;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EscapingTest {

    @Test
    public void escapeIllegalJcrChars_leavesPlainNameUntouched() {
        assertThat(Escaping.escapeIllegalJcrChars("thread_dump.txt")).isEqualTo("thread_dump.txt");
    }

    @Test
    public void escapeIllegalJcrChars_encodesColonInIsoTimestamp() {
        // ISO-8601 dump filenames contain ':' which is illegal in an unqualified JCR name
        assertThat(Escaping.escapeIllegalJcrChars("2026-01-22T14:15:28Z"))
                .isEqualTo("2026-01-22T14%3A15%3A28Z");
    }

    @Test
    public void escapeIllegalJcrChars_encodesEachIllegalCharAsUppercaseHex() {
        assertThat(Escaping.escapeIllegalJcrChars("[]*|:"))
                .isEqualTo("%5B%5D%2A%7C%3A");
    }

    @Test
    public void escapeIllegalJcrChars_emptyStringStaysEmpty() {
        assertThat(Escaping.escapeIllegalJcrChars("")).isEmpty();
    }

    // (#9) Round-trip: unescape(escape(x)) == x for every character in the illegal set.
    @Test
    public void roundTrip_escapeFollowedByUnescape_restoresOriginal() {
        // Each character that escapeIllegalJcrChars() encodes must survive a round-trip.
        final String[] inputs = {
            "[",
            "]",
            "*",
            "|",
            ":",
            "2026-01-22T14:15:28Z",       // ISO-8601 timestamp
            "heap[0].hprof",               // brackets
            "a|b*c:d[e]f",                 // all illegal chars combined
            "plain_name_no_specials.txt",  // no-op: should be unchanged
        };
        for (String original : inputs) {
            final String escaped = Escaping.escapeIllegalJcrChars(original);
            final String restored = Escaping.unescapeIllegalJcrChars(escaped);
            assertThat(restored)
                    .as("round-trip for: %s", original)
                    .isEqualTo(original);
        }
    }
}
