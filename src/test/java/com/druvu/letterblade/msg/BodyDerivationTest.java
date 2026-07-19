package com.druvu.letterblade.msg;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.letterblade.msg.MsgService.BodyView;

import org.testng.annotations.Test;

/**
 * Pure unit tests of {@link MsgService#deriveBody}. Because no real Outlook {@code .msg} fixture
 * carries a native {@code getBodyHTML()} (Outlook stores RTF), the HTML and TEXT_ONLY branches are
 * covered here with synthetic inputs; the RTF_CONVERTED branch is additionally exercised against
 * real fixtures in {@link MsgServiceTest}.
 */
public class BodyDerivationTest {

	@Test
	public void nativeHtmlWinsOverConverted() {
		BodyView b = MsgService.deriveBody("<p>native</p>", "<p>converted</p>", "plain");
		assertThat(b.branch()).isEqualTo(BodyBranch.HTML);
		assertThat(b.bodyHtml()).isEqualTo("<p>native</p>");
	}

	@Test
	public void convertedHtmlWhenNoNativeHtml() {
		BodyView b = MsgService.deriveBody(null, "<p>converted</p>", "plain");
		assertThat(b.branch()).isEqualTo(BodyBranch.RTF_CONVERTED);
		assertThat(b.bodyHtml()).isEqualTo("<p>converted</p>");
	}

	@Test
	public void textOnlyWhenBothHtmlSourcesBlank() {
		BodyView b = MsgService.deriveBody("   ", "\t\n", "only text");
		assertThat(b.branch()).isEqualTo(BodyBranch.TEXT_ONLY);
		assertThat(b.bodyHtml()).isNull();
		assertThat(b.plainText()).isEqualTo("only text");
	}

	@Test
	public void plainTextPrefersBodyText() {
		BodyView b = MsgService.deriveBody(null, "<p>converted body</p>", "explicit plain");
		assertThat(b.plainText()).isEqualTo("explicit plain");
	}

	@Test
	public void plainTextFallsBackToJsoupTextOfHtml() {
		BodyView b = MsgService.deriveBody("<p>Hello <b>world</b></p>", null, "  ");
		assertThat(b.branch()).isEqualTo(BodyBranch.HTML);
		assertThat(b.plainText()).isEqualTo("Hello world");
	}

	@Test
	public void plainTextIsEmptyWhenNothingAvailable() {
		BodyView b = MsgService.deriveBody(null, null, null);
		assertThat(b.branch()).isEqualTo(BodyBranch.TEXT_ONLY);
		assertThat(b.bodyHtml()).isNull();
		assertThat(b.plainText()).isEmpty();
	}
}
