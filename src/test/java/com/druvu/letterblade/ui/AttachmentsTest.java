package com.druvu.letterblade.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

/** Unit tests for the JavaFX-free attachment presentation helpers. */
public class AttachmentsTest {

    @Test
    public void humanSizeScalesUnits() {
        assertThat(Attachments.humanSize(0)).isEqualTo("0 B");
        assertThat(Attachments.humanSize(401)).isEqualTo("401 B");
        assertThat(Attachments.humanSize(1023)).isEqualTo("1023 B");
        assertThat(Attachments.humanSize(1024)).isEqualTo("1.0 KB");
        assertThat(Attachments.humanSize(1677)).isEqualTo("1.6 KB");
        assertThat(Attachments.humanSize(5L * 1024 * 1024)).isEqualTo("5.0 MB");
        assertThat(Attachments.humanSize(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
        assertThat(Attachments.humanSize(4L * 1024 * 1024 * 1024 * 1024)).isEqualTo("4.0 TB");
    }

    @Test
    public void humanSizePromotesRoundingBoundary() {
        // 1048575 B == 1023.999 KB, which must round up to 1.0 MB rather than print "1024.0 KB".
        assertThat(Attachments.humanSize(1024L * 1024 - 1)).isEqualTo("1.0 MB");
        assertThat(Attachments.humanSize(1024L * 1024 * 1024 - 1)).isEqualTo("1.0 GB");
    }

    @Test
    public void humanSizeUnknownRendersQuestionMark() {
        assertThat(Attachments.humanSize(-1)).isEqualTo("?");
    }

    @Test
    public void displayNamePrefersLongThenShortThenGeneric() {
        assertThat(Attachments.displayName("report.pdf", "r.pdf", "pdf")).isEqualTo("report.pdf");
        assertThat(Attachments.displayName(null, "r.pdf", "pdf")).isEqualTo("r.pdf");
        assertThat(Attachments.displayName("  ", "  ", "eml")).isEqualTo("attachment.eml");
        assertThat(Attachments.displayName(null, null, null)).isEqualTo("attachment");
        assertThat(Attachments.displayName(null, null, "  ")).isEqualTo("attachment");
    }

    @Test
    public void safeFileNameStripsDirectoriesAndControlChars() {
        assertThat(Attachments.safeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(Attachments.safeFileName("a/b/c.txt")).isEqualTo("c.txt");
        assertThat(Attachments.safeFileName("C:\\Windows\\evil.exe")).isEqualTo("evil.exe");
        // Control characters (here TAB 0x09 and a bare newline 0x0a) are replaced, not passed through.
        assertThat(Attachments.safeFileName("a\tb\nc.txt")).isEqualTo("a_b_c.txt");
        assertThat(Attachments.safeFileName("..")).isEqualTo("attachment");
        assertThat(Attachments.safeFileName(".")).isEqualTo("attachment");
        assertThat(Attachments.safeFileName("")).isEqualTo("attachment");
        assertThat(Attachments.safeFileName("   ")).isEqualTo("attachment");
        assertThat(Attachments.safeFileName(null)).isEqualTo("attachment");
    }
}
