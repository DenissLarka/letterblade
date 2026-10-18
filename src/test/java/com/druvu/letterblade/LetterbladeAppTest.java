package com.druvu.letterblade;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

/** Unit test for the start-up argument clean-up: a quoted dev-run path is unwrapped, anything else is left alone. */
public class LetterbladeAppTest {

    @Test
    public void unquotedStripsOnlyAWrappingPairOfQuotes() {
        // No file is read: the strings are what the javafx plugin hands the app for a path with a space in it.
        assertThat(LetterbladeApp.unquoted("\"/mail/with space.msg\"")).isEqualTo("/mail/with space.msg");
        assertThat(LetterbladeApp.unquoted("/tmp/plain.msg")).isEqualTo("/tmp/plain.msg");
        assertThat(LetterbladeApp.unquoted("\"")).isEqualTo("\"");
        assertThat(LetterbladeApp.unquoted("")).isEmpty();
    }
}
