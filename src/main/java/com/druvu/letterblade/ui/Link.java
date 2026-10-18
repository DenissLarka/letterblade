package com.druvu.letterblade.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A link the reader clicked in the rendered message: the address it leads to and the text it showed. The dialog puts
 * the two side by side, because a text that says one thing over an address that goes elsewhere is the usual phishing
 * trick, and the reader should see it before anything opens.
 *
 * <p>JavaFX-free so it can be unit-tested without a toolkit, like {@link Attachments}.
 *
 * @param address the {@code href} as the sanitizer let it through: absolute, one of http, https, ftp, mailto
 * @param text the link's visible text, whitespace collapsed; empty for an image-only link
 * @author Deniss Larka <br>
 *     on 18 Oct 2026
 */
record Link(String address, String text) {

    /** A long link text is cut in the dialog; the address is shown whole, it is the part that matters. */
    private static final int TEXT_LIMIT = 160;

    Link {
        address = address == null ? "" : address.strip();
        text = text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    /** A {@code mailto:} link: the system hands it to the mail app, not the browser. */
    boolean isMail() {
        return address.toLowerCase(Locale.ROOT).startsWith("mailto:");
    }

    /** What the system will open the link with, for the dialog's wording. */
    String opensIn() {
        return isMail() ? "your mail app" : "your browser";
    }

    /** True when the visible text tells the reader something the address does not: it exists and is not the address. */
    boolean textDiffers() {
        return !text.isEmpty() && !text.equals(address);
    }

    /** The visible text for the dialog, cut to {@link #TEXT_LIMIT} characters with an ellipsis. */
    String displayText() {
        return text.length() <= TEXT_LIMIT ? text : text.substring(0, TEXT_LIMIT) + "…";
    }

    /**
     * The address as a {@link URI} for {@link java.awt.Desktop}. A space is the one defect real mail HTML commonly
     * carries in an href, so it is encoded; anything else malformed is refused, not guessed at.
     */
    URI uri() throws URISyntaxException {
        return new URI(address.replace(" ", "%20"));
    }
}
