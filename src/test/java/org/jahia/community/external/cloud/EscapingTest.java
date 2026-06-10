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
}
