package com.druvu.letterblade.render;

import com.druvu.letterblade.msg.ParsedMessage;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;

/**
 * Turns a message body into HTML that is safe to hand to a JavaScript-disabled {@code WebView}. WebView has no network
 * interceptor, so <strong>this class is the security boundary</strong>: any content it lets through will be rendered
 * verbatim.
 *
 * <p>Design invariant: the jsoup {@link Cleaner} is the enforcer. The pre-pass only <em>rewrites</em> inline
 * ({@code cid:}) images to {@code data:} URIs and <em>counts</em> remote images; it never relies on its own string
 * checks to remove anything unsafe. Whatever the pre-pass misses must still die in the Cleaner. JavaScript is never
 * enabled, in either mode.
 */
public final class Sanitizer {

    /** Whether remote ({@code http/https}) content is blocked (default) or allowed (user opt-in). */
    public enum Mode {
        DEFAULT,
        ALLOW_REMOTE
    }

    /**
     * Sanitized body HTML plus how many remote images were blocked (drives the "N remote images blocked" bar). Zero in
     * {@link Mode#ALLOW_REMOTE}, where nothing is blocked.
     */
    public record SafeHtml(String html, int blockedRemoteCount) {}

    /**
     * Sanitizes a parsed message's body. A text-only message (no HTML body) is rendered as its HTML-escaped plain text
     * wrapped in {@code <pre>}.
     *
     * @param message the parsed message
     * @param mode remote-content policy
     * @return safe HTML for {@code WebView.loadContent}, and the blocked-remote-image count
     */
    public SafeHtml sanitize(ParsedMessage message, Mode mode) {
        if (message.bodyHtml() == null) {
            return new SafeHtml(escapedPreText(message.plainText()), 0);
        }
        return sanitizeHtml(message.bodyHtml(), dataUris(message.cidMap()), mode);
    }

    /**
     * The core pipeline: pre-pass (rewrite {@code cid:} images, count remote images) then the jsoup {@link Cleaner}.
     * Package-private and free of parser types so the hostile-input suite can drive it with plain strings and a
     * synthetic {@code cid ->} data-URI map.
     *
     * @param html the (untrusted) body HTML
     * @param cidToDataUri resolved inline images: Content-ID to a ready {@code data:} URI
     * @param mode remote-content policy
     * @return sanitized HTML and the blocked-remote-image count
     */
    SafeHtml sanitizeHtml(String html, Map<String, String> cidToDataUri, Mode mode) {
        final Document doc = Jsoup.parse(html == null ? "" : html);

        int blockedRemote = 0;
        for (Element img : doc.select("img")) {
            final String src = img.attr("src").trim();
            final String scheme = src.toLowerCase(Locale.ROOT);
            if (scheme.startsWith("cid:")) {
                final String dataUri = cidToDataUri.get(src.substring("cid:".length()));
                if (dataUri == null) {
                    img.remove(); // unresolvable inline image -> drop it
                } else {
                    img.attr("src", dataUri);
                }
            } else if (scheme.startsWith("http://") || scheme.startsWith("https://")) {
                blockedRemote++; // count only; the Cleaner strips the src below
            }
        }

        if (mode == Mode.DEFAULT) {
            stripFetchingStyles(doc);
        }

        final Document clean = new Cleaner(safelist(mode)).clean(doc);
        return new SafeHtml(clean.body().html(), mode == Mode.DEFAULT ? blockedRemote : 0);
    }

    /**
     * Wraps HTML-escaped plain text in {@code <pre>}. Escaping is mandatory: plain text can contain literal
     * {@code <script>} that must never become live markup.
     */
    static String escapedPreText(String plainText) {
        final Document doc = Document.createShell("");
        doc.body().appendElement("pre").text(plainText == null ? "" : plainText);
        return doc.body().html();
    }

    /**
     * In default mode, delete any {@code style} attribute that can fetch a remote resource or hide one behind a CSS
     * escape ({@code url}, {@code expression}, {@code image-set}, {@code @}, or a backslash). Plain colour/font/layout
     * styles (including {@code rgb(...)}) survive.
     */
    private static void stripFetchingStyles(Document doc) {
        for (Element el : doc.select("[style]")) {
            final String value = el.attr("style").toLowerCase(Locale.ROOT);
            if (value.contains("url")
                    || value.contains("expression")
                    || value.contains("image-set")
                    || value.contains("@")
                    || value.contains("\\")) {
                el.removeAttr("style");
            }
        }
    }

    private static Safelist safelist(Mode mode) {
        final Safelist safelist = Safelist.relaxed().addAttributes(":all", "style");
        // Reduce img src to data: only. A remote img the pre-pass missed (odd whitespace, exotic
        // scheme casing) still has its src stripped here - the Cleaner is the real enforcer.
        safelist.removeProtocols("img", "src", "http", "https").addProtocols("img", "src", "data");
        if (mode == Mode.ALLOW_REMOTE) {
            safelist.addProtocols("img", "src", "http", "https");
        }
        return safelist;
    }

    private static Map<String, String> dataUris(Map<String, OutlookFileAttachment> cidMap) {
        final Map<String, String> out = new HashMap<>();
        cidMap.forEach((cid, att) -> {
            final String mime = att.getMimeTag() != null ? att.getMimeTag() : "image/png";
            out.put(cid, "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(att.getData()));
        });
        return out;
    }
}
