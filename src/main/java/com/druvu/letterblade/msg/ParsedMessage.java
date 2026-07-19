package com.druvu.letterblade.msg;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookMsgAttachment;

/**
 * Immutable view-model of a parsed {@code .msg} message - everything the UI needs, and nothing that
 * ties it to the parser's mutable {@code OutlookMessage}. Produced by {@link MsgService#parse}.
 *
 * <p>The collection components are defensively copied to unmodifiable collections in the canonical
 * constructor. Their <em>elements</em> are the parser's attachment objects, shared by reference on
 * purpose: they carry the message's {@code byte[]} payloads, so the model holds them rather than
 * duplicating the bytes.
 *
 * @param subject          message subject (may be null/blank)
 * @param fromName         sender display name (may be null)
 * @param fromEmail        sender e-mail address (may be null)
 * @param displayTo        pre-formatted {@code To} display string (may be blank)
 * @param displayCc        pre-formatted {@code Cc} display string (may be blank)
 * @param date             sent time - client-submit time if present, else the message date (nullable)
 * @param bodyHtml         the HTML to sanitize and render, or null for a text-only message
 * @param plainText        never-null plain-text projection (feeds Plain mode and Copy)
 * @param pickBranch       which source produced {@link #bodyHtml}
 * @param attachments      real file attachments (inline images excluded)
 * @param embeddedMessages nested {@code .msg} attachments (embedded Outlook messages)
 * @param cidMap           inline attachments keyed by Content-ID (targets of {@code cid:} URIs)
 */
public record ParsedMessage(
		String subject,
		String fromName,
		String fromEmail,
		String displayTo,
		String displayCc,
		Instant date,
		String bodyHtml,
		String plainText,
		BodyBranch pickBranch,
		List<OutlookFileAttachment> attachments,
		List<OutlookMsgAttachment> embeddedMessages,
		Map<String, OutlookFileAttachment> cidMap) {

	public ParsedMessage {
		attachments = List.copyOf(attachments);
		embeddedMessages = List.copyOf(embeddedMessages);
		cidMap = Map.copyOf(cidMap);
	}
}
