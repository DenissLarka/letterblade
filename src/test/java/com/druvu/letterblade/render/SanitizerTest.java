package com.druvu.letterblade.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer.Mode;
import com.druvu.letterblade.render.Sanitizer.SafeHtml;
import java.io.File;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * Hostile-input suite for the security boundary. Each test proves the Cleaner removes something unsafe, or that a
 * legitimate construct survives.
 */
public class SanitizerTest {

    private final Sanitizer sanitizer = new Sanitizer();

    private String cleanDefault(String html) {
        return sanitizer.sanitizeHtml(html, Map.of(), Mode.DEFAULT).html();
    }

    @Test
    public void scriptTagRemoved() {
        assertThat(cleanDefault("<p>hi</p><script>alert(1)</script>"))
                .doesNotContain("script")
                .contains("hi");
    }

    @Test
    public void imgOnerrorAttributeRemoved() {
        final String out = cleanDefault("<img src=\"data:image/png;base64,QUJD\" onerror=\"alert(1)\">");
        assertThat(out).doesNotContain("onerror");
    }

    @Test
    public void iframeObjectFormRemoved() {
        final String out = cleanDefault("<iframe src=\"x\"></iframe><object data=\"y\"></object><form><input></form>");
        assertThat(out).doesNotContain("iframe").doesNotContain("object").doesNotContain("<form");
    }

    @Test
    public void javascriptHrefRemoved() {
        assertThat(cleanDefault("<a href=\"javascript:alert(1)\">x</a>")).doesNotContain("javascript:");
    }

    @Test
    public void struckAndRevisedTextKeepsItsTags() {
        // Outlook writes struck-through text as <s>; relaxed() alone would drop the tag and keep the words.
        final String out =
                cleanDefault("<p><s><span style=\"color:red\">done</span></s> <del>old</del><ins>new</ins></p>");
        assertThat(out).contains("<s>").contains("<del>").contains("<ins>").contains("color:red");
    }

    @Test
    public void wordLayoutAttributesSurvive() {
        // A Word newsletter centres its column with <div align=center> and top-aligns cells with valign; relaxed()
        // alone drops both, which left every such column against the left edge.
        final String out = cleanDefault("<div align=\"center\"><table width=\"600\"><tr>"
                + "<td valign=\"top\" align=\"right\" onclick=\"x()\">x</td></tr></table></div>");
        assertThat(out)
                .contains("<div align=\"center\">")
                .contains("valign=\"top\"")
                .contains("align=\"right\"")
                .doesNotContain("onclick");
    }

    @Test
    public void messageStylesheetSurvivesMinusFetchingRules() {
        final String out = cleanDefault(
                "<html><head><style><!-- p.MsoNormal{margin:0;font-size:11pt}"
                        + " @import url(http://evil.example/x.css); .t{background:url(http://evil.example/p.png)}"
                        + " @font-face{font-family:X} b{color:red} --></style></head><body><p class=MsoNormal>x</p></body></html>");
        assertThat(out)
                .contains("<style>")
                .contains("p.MsoNormal{margin:0;font-size:11pt}")
                .contains("b{color:red}");
        assertThat(out)
                .doesNotContain("evil.example")
                .doesNotContain("@import")
                .doesNotContain("@font-face")
                .doesNotContain("url(");
    }

    @Test
    public void messageStylesheetInAllowRemoteModeMayFetchLikeInlineStylesDo() {
        final String out = sanitizer
                .sanitizeHtml(
                        "<style>.t{background:url(http://img.example/p.png)}</style><p class=t>x</p>",
                        Map.of(),
                        Mode.ALLOW_REMOTE)
                .html();
        assertThat(out).contains("url(http://img.example/p.png)");
    }

    @Test
    public void mailWithoutStylesheetGetsTheReadingPaneDefaults() {
        assertThat(cleanDefault("<p>x</p>"))
                .contains("<style>")
                .contains("font-family:'Aptos'")
                .contains("font-size:11pt");
    }

    @Test
    public void supplementaryCharactersLeaveAsNumericReferences() {
        // WebView.loadContent garbles raw surrogate pairs into U+FFFD; character references survive the trip.
        assertThat(cleanDefault("<p>🧱 ok</p>")).contains("&#x1f9f1;").doesNotContain("🧱");
    }

    @Test
    public void emojiRunsGetTheEmojiFontSpan() {
        final String out = cleanDefault("<p>done ✅ 🧱📊 tail</p>");
        assertThat(out).contains("<span class=\"lb-emoji\" style=\"font-family:'Segoe UI Emoji'");
        assertThat(out).contains(">&#x2705;</span>").contains(">&#x1f9f1;&#x1f4ca;</span>");
        assertThat(out).contains("done ").contains(" tail");
    }

    @Test
    public void plainTextPathEscapesAndMarksEmojiToo() {
        final String out = Sanitizer.escapedPreText("<script>x 😀");
        assertThat(out).contains("&lt;script&gt;").contains("&#x1f600;").contains("Segoe UI Emoji");
    }

    @Test
    public void remoteImageBlockedAndCountedInDefault() {
        final SafeHtml r =
                sanitizer.sanitizeHtml("<img src=\"http://evil.example/track.png\">", Map.of(), Mode.DEFAULT);
        assertThat(r.blockedRemoteCount()).isEqualTo(1);
        assertThat(r.html()).doesNotContain("evil.example");
    }

    @Test
    public void remoteImageWithLeadingWhitespaceStillBlockedByCleaner() {
        // The pre-pass trims for its count, but the point is the Cleaner enforces regardless.
        final SafeHtml r = sanitizer.sanitizeHtml("<img src=\"   http://evil.example/x.png\">", Map.of(), Mode.DEFAULT);
        assertThat(r.html()).doesNotContain("evil.example");
    }

    @Test
    public void remoteImageKeptInAllowRemoteMode() {
        final SafeHtml r =
                sanitizer.sanitizeHtml("<img src=\"http://cdn.example/x.png\">", Map.of(), Mode.ALLOW_REMOTE);
        assertThat(r.html()).contains("http://cdn.example/x.png");
        assertThat(r.blockedRemoteCount()).isZero();
    }

    @Test
    public void cidImageRewrittenToDataAndSurvivesCleaning() {
        final Map<String, String> cid = Map.of("thumbsup", "data:image/png;base64,QUJD");
        final SafeHtml r = sanitizer.sanitizeHtml("<img src=\"cid:thumbsup\">", cid, Mode.DEFAULT);
        assertThat(r.html()).contains("data:image/png;base64,QUJD");
        assertThat(r.blockedRemoteCount()).isZero();
    }

    @Test
    public void unresolvableCidImageDropped() {
        final SafeHtml r = sanitizer.sanitizeHtml("<img src=\"cid:missing\">", Map.of(), Mode.DEFAULT);
        assertThat(r.html()).doesNotContain("cid:").doesNotContain("<img");
    }

    @Test
    public void fetchingStyleAttributesStrippedInDefault() {
        assertThat(cleanDefault("<p style=\"background:url(http://evil/x.png)\">a</p>"))
                .doesNotContain("url")
                .doesNotContain("style=");
        // CSS-escape trick: u\72l -> "url"; the backslash is the tell.
        assertThat(cleanDefault("<p style=\"background:u\\72l(http://evil/x.png)\">b</p>"))
                .doesNotContain("evil")
                .doesNotContain("style=");
    }

    @Test
    public void plainColourStyleSurvivesInDefault() {
        assertThat(cleanDefault("<p style=\"color:rgb(1,2,3)\">c</p>")).contains("color:rgb(1,2,3)");
    }

    @Test
    public void escapedPlainTextRendersScriptAsText() {
        final String out = Sanitizer.escapedPreText("<script>alert(1)</script>");
        assertThat(out).contains("&lt;script&gt;").doesNotContain("<script>").contains("<pre>");
    }

    @Test
    public void realFixtureCidImageBecomesDataUri() throws Exception {
        final File f = new File(getClass()
                .getResource("/test-messages/html-with-attachment-and-embedded-image.msg")
                .toURI());
        final ParsedMessage m = new MsgService().parse(f);
        final SafeHtml safe = sanitizer.sanitize(m, Mode.DEFAULT);
        assertThat(safe.html()).contains("data:image/png;base64,").doesNotContain("cid:");
        assertThat(safe.blockedRemoteCount()).isZero();
    }
}
