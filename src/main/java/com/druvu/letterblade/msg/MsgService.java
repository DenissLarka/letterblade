package com.druvu.letterblade.msg;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.simplejavamail.outlookmessageparser.OutlookMessageParser;
import org.simplejavamail.outlookmessageparser.model.OutlookMessage;
import org.simplejavamail.outlookmessageparser.model.OutlookMsgAttachment;

/**
 * Parses an Outlook {@code .msg} file into an immutable {@link ParsedMessage}. Synchronous and free
 * of any JavaFX dependency - running it off the FX thread is the caller's job (the UI does so via the
 * toolkit executor).
 */
public final class MsgService {

	/**
	 * Parses the given {@code .msg} file.
	 *
	 * @param file the {@code .msg} file to read
	 * @return the parsed, immutable message view-model
	 * @throws MsgParseException if the file is not a readable Outlook message (corrupt, wrong format,
	 *                           unreadable) - the single checked exception the UI catches
	 */
	public ParsedMessage parse(File file) throws MsgParseException {
		Objects.requireNonNull(file, "file");

		final OutlookMessage msg;
		try {
			msg = new OutlookMessageParser().parseMsg(file);
		} catch (IOException | RuntimeException e) {
			// The app opens files from anywhere, so any parser failure - POI's NotOLE2FileException
			// on a non-OLE2 file, a malformed structure, etc. - is normalised to one checked type.
			throw new MsgParseException("Not a readable Outlook .msg file: " + file.getName(), e);
		}

		final BodyView body = deriveBody(msg.getBodyHTML(), msg.getConvertedBodyHTML(), msg.getBodyText());

		final List<OutlookMsgAttachment> embedded = msg.getOutlookAttachments().stream()
				.filter(OutlookMsgAttachment.class::isInstance)
				.map(OutlookMsgAttachment.class::cast)
				.toList();

		return new ParsedMessage(
				msg.getSubject(), msg.getFromName(), msg.getFromEmail(),
				msg.getDisplayTo(), msg.getDisplayCc(), sentTime(msg),
				body.bodyHtml(), body.plainText(), body.branch(),
				msg.fetchTrueAttachments(), embedded, msg.fetchCIDMap());
	}

	/**
	 * The message's sent time as an {@link Instant}: client-submit time if present, else the message
	 * date. The parser exposes these only as {@code java.util.Date}, so the conversion happens here at
	 * the boundary and nothing but {@code java.time} escapes into the model.
	 *
	 * @return the sent instant, or {@code null} if the message carries no timestamp
	 */
	private static Instant sentTime(OutlookMessage msg) {
		final var sent = msg.getClientSubmitTime() != null ? msg.getClientSubmitTime() : msg.getDate();
		return sent == null ? null : sent.toInstant();
	}

	/**
	 * Chooses the body to render and its plain-text projection, mirroring how Outlook stores bodies:
	 * a native HTML body wins, otherwise the RTF-to-HTML conversion, otherwise plain text only. Pure
	 * and side-effect free so every branch is unit-testable without a real message.
	 *
	 * @param bodyHtml      value of {@code getBodyHTML()} (often null - Outlook stores RTF)
	 * @param convertedHtml value of {@code getConvertedBodyHTML()} (the RTF-to-HTML conversion)
	 * @param bodyText      value of {@code getBodyText()}
	 * @return the chosen HTML (nullable), the never-null plain text, and the branch taken
	 */
	static BodyView deriveBody(String bodyHtml, String convertedHtml, String bodyText) {
		final String html;
		final BodyBranch branch;
		if (isNotBlank(bodyHtml)) {
			html = bodyHtml;
			branch = BodyBranch.HTML;
		} else if (isNotBlank(convertedHtml)) {
			html = convertedHtml;
			branch = BodyBranch.RTF_CONVERTED;
		} else {
			html = null;
			branch = BodyBranch.TEXT_ONLY;
		}

		final String plainText;
		if (isNotBlank(bodyText)) {
			plainText = bodyText;
		} else if (html != null) {
			plainText = Jsoup.parse(html).text();
		} else {
			plainText = "";
		}

		return new BodyView(html, plainText, branch);
	}

	private static boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	/** The chosen body to render, its plain-text projection, and which branch produced them. */
	record BodyView(String bodyHtml, String plainText, BodyBranch branch) {
	}
}
