package com.druvu.letterblade.ui;

import java.util.Locale;

/**
 * Pure, JavaFX-free helpers for presenting {@code .msg} attachments: a human-readable size, the chip display label, and
 * a safe on-disk file name. Kept out of {@link MainView} so they can be unit-tested without a JavaFX runtime.
 */
final class Attachments {

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private Attachments() {}

    /**
     * A human-readable size such as {@code "812 B"}, {@code "1.6 KB"}, {@code "3.0 MB"}. A negative size (the parser's
     * "unknown") renders as {@code "?"}.
     */
    static String humanSize(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        // Guard the rounding boundary: 1048575 B is 1023.999 KB, which "%.1f" would print as "1024.0 KB" - promote it.
        if (unit < UNITS.length - 1 && Math.round(value * 10) >= 10240) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }

    /**
     * The label for an attachment chip: the long file name, else the short file name, else a generic
     * {@code "attachment"} (with the extension appended when the parser knows one) - some parts, such as
     * delivery-status reports, carry no name at all.
     */
    static String displayName(String longFilename, String filename, String extension) {
        if (isNotBlank(longFilename)) {
            return longFilename.strip();
        }
        if (isNotBlank(filename)) {
            return filename.strip();
        }
        return isNotBlank(extension) ? "attachment." + extension.strip() : "attachment";
    }

    /**
     * Reduces an attacker-controlled attachment name to a single safe path segment for writing under the app home:
     * strips any directory components and control characters so a crafted name such as {@code "../../evil"} cannot
     * escape the target directory.
     */
    static String safeFileName(String name) {
        String base = name == null ? "" : name;
        final int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\x00-\\x1f]", "_").strip();
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            base = "attachment";
        }
        return base;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
