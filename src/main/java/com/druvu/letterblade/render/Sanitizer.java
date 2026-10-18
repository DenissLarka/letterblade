package com.druvu.letterblade.render;

import com.druvu.letterblade.msg.ParsedMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
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
 *
 * <p>Two steps run <em>after</em> the Cleaner and are presentation only, so they add nothing an attacker controls: runs
 * of emoji are wrapped in a {@code <span>} carrying one literal font-family style, and the result is serialized as
 * ASCII with character references. Both exist for the JavaFX WebKit port (measured on JavaFX 27): {@code loadContent}
 * garbles supplementary-plane characters (each surrogate pair arrives as replacement characters), and even as
 * references they only find a glyph when an emoji family is named explicitly - monochrome, since JavaFX draws no colour
 * emoji.
 */
public final class Sanitizer {

    /** Marks an emoji run for the view, which may swap the span for an image it renders itself. */
    public static final String EMOJI_SPAN_CLASS = "lb-emoji";

    /**
     * Defaults for a message that brings no stylesheet: Outlook's reading-pane look (Aptos, then the platform's UI
     * font, 11pt) instead of WebKit's 16px serif. A message's own rules follow and win.
     */
    static final String BASE_CSS =
            "body{font-family:'Aptos','Segoe UI',Calibri,Helvetica,Arial,sans-serif;font-size:11pt;margin:8px}\n";

    private static final String EMOJI_FONT_STYLE =
            "font-family:'Segoe UI Emoji','Apple Color Emoji','Noto Color Emoji',sans-serif";

    /** An emoji followed by any number of emoji, variation selectors, joiners, skin tones or keycap marks. */
    private static final Pattern EMOJI_RUN =
            Pattern.compile("[\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}]"
                    + "(?:[\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}\\x{FE0F}\\x{200D}\\x{20E3}])*");

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

        // One <style> at the top of the body: the reading-pane defaults, then the message's own rules minus
        // anything that fetches. The Cleaner keeps it because "style" is in the safelist; a <style> in <head>
        // would be lost (the Cleaner copies the body only), which is where Outlook puts Word's stylesheet.
        final String css = BASE_CSS + safeStyleRules(doc, mode);
        doc.select("style").remove();
        doc.body().prependElement("style").appendChild(new DataNode(css));

        final Document clean = new Cleaner(safelist(mode)).clean(doc);
        return new SafeHtml(forWebView(clean), mode == Mode.DEFAULT ? blockedRemote : 0);
    }

    /**
     * Wraps HTML-escaped plain text in {@code <pre>}. Escaping is mandatory: plain text can contain literal
     * {@code <script>} that must never become live markup.
     */
    static String escapedPreText(String plainText) {
        final Document doc = Document.createShell("");
        doc.body().appendElement("pre").text(plainText == null ? "" : plainText);
        return forWebView(doc);
    }

    /** The post-Cleaner presentation steps (see the class comment): emoji spans, then ASCII serialization. */
    private static String forWebView(Document safe) {
        markEmojiRuns(safe.body());
        safe.outputSettings().charset(StandardCharsets.US_ASCII);
        return safe.body().html();
    }

    private static void markEmojiRuns(Element root) {
        final List<TextNode> textNodes = new ArrayList<>();
        root.traverse((node, depth) -> {
            if (node instanceof TextNode text) {
                textNodes.add(text);
            }
        });
        for (TextNode text : textNodes) {
            wrapEmojiRuns(text);
        }
    }

    /** Splits the text node around each emoji run and wraps the run; the text itself is never altered. */
    private static void wrapEmojiRuns(TextNode node) {
        TextNode current = node;
        Matcher matcher = EMOJI_RUN.matcher(current.getWholeText());
        while (matcher.find()) {
            final int start = matcher.start();
            final int length = matcher.end() - start;
            final TextNode run = start == 0 ? current : current.splitText(start);
            final TextNode rest = length < run.getWholeText().length() ? run.splitText(length) : null;
            run.wrap("<span class=\"" + EMOJI_SPAN_CLASS + "\" style=\"" + EMOJI_FONT_STYLE + "\"></span>");
            if (rest == null) {
                return;
            }
            current = rest;
            matcher = EMOJI_RUN.matcher(current.getWholeText());
        }
    }

    /**
     * The message's own stylesheets, rule by rule, minus every rule that could fetch or escape: any at-rule (fonts,
     * imports, media), {@code expression}, {@code behavior}, bindings, backslash escapes, and in default mode
     * {@code url}/{@code image-set}. Word's HTML keeps its paragraph metrics and fonts there, which is most of what
     * makes a message look as it did in Outlook. Splitting on the closing brace is deliberately crude: a rule that a
     * stray brace cuts in two is checked twice, never skipped.
     */
    static String safeStyleRules(Document doc, Mode mode) {
        final StringBuilder out = new StringBuilder();
        for (Element style : doc.select("style")) {
            final String css = style.data().replace("<!--", "").replace("-->", "");
            for (String rule : css.split("}")) {
                final String trimmed = rule.strip();
                if (!trimmed.isEmpty() && isSafeRule(trimmed, mode)) {
                    out.append(trimmed).append("}\n");
                }
            }
        }
        return out.toString();
    }

    private static boolean isSafeRule(String rule, Mode mode) {
        final String lower = rule.toLowerCase(Locale.ROOT);
        if (lower.contains("@")
                || lower.contains("\\")
                || lower.contains("expression")
                || lower.contains("behavior")
                || lower.contains("binding")) {
            return false;
        }
        return mode == Mode.ALLOW_REMOTE || !(lower.contains("url") || lower.contains("image-set"));
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
        // relaxed() predates HTML5: it knows <strike> but not <s>, <del>, <ins> - and Outlook writes struck
        // text as <s>. Purely presentational, no attributes of their own, so they are safe to let through.
        // "style" (the element) carries only the stylesheet sanitizeHtml assembled; the Cleaner copies its text.
        // relaxed() also predates the presentation attributes Word lays a newsletter out with: <div align=center> is
        // the one thing that centres its column, valign the one thing that top-aligns its cells. Both take a fixed
        // set of keywords, never a URL, so they are safe on every tag.
        final Safelist safelist = Safelist.relaxed()
                .addTags("s", "del", "ins", "style")
                .addAttributes(":all", "style", "align", "valign");
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
