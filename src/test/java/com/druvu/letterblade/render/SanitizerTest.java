package com.druvu.letterblade.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer.Mode;
import com.druvu.letterblade.render.Sanitizer.SafeHtml;

import org.testng.annotations.Test;

/**
 * Hostile-input suite for the security boundary. Each test proves the Cleaner removes something
 * unsafe, or that a legitimate construct survives.
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
	public void remoteImageBlockedAndCountedInDefault() {
		final SafeHtml r = sanitizer.sanitizeHtml("<img src=\"http://evil.example/track.png\">", Map.of(), Mode.DEFAULT);
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
		final SafeHtml r = sanitizer.sanitizeHtml("<img src=\"http://cdn.example/x.png\">", Map.of(), Mode.ALLOW_REMOTE);
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
				.doesNotContain("url").doesNotContain("style=");
		// CSS-escape trick: u\72l -> "url"; the backslash is the tell.
		assertThat(cleanDefault("<p style=\"background:u\\72l(http://evil/x.png)\">b</p>"))
				.doesNotContain("evil").doesNotContain("style=");
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
				.getResource("/test-messages/html-with-attachment-and-embedded-image.msg").toURI());
		final ParsedMessage m = new MsgService().parse(f);
		final SafeHtml safe = sanitizer.sanitize(m, Mode.DEFAULT);
		assertThat(safe.html()).contains("data:image/png;base64,").doesNotContain("cid:");
		assertThat(safe.blockedRemoteCount()).isZero();
	}
}
