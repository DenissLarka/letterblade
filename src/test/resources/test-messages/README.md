# Test fixtures — provenance

The `.msg` files in this directory are copied verbatim from the
[outlook-message-parser](https://github.com/bbottema/outlook-message-parser) project's own
`src/test/resources/test-messages/` (Apache License 2.0), fetched 2026-07-19. They are renamed for
space-free classpath loading; the originals are listed below.

| local name | upstream name | used for |
|---|---|---|
| `html-with-attachment-and-embedded-image.msg` | `HTML mail with replyto and attachment and embedded image.msg` | envelope + 2 attachments + `cid:` inline image |
| `rtf-sample-email.msg` | `issue-16-rtf-sample-email.msg` | RTF-only body (converted-HTML branch), no attachments |
| `attachments.msg` | `attachments.msg` | attachments with null filenames (delivery-status parts) |
| `nested-simple-mail.msg` | `nested simple mail.msg` | one embedded (nested) `.msg` message |

Note: no upstream fixture exposes a native `getBodyHTML()` — every Outlook `.msg` stores its body as
RTF, so all of these take the `RTF_CONVERTED` branch. The native-HTML and TEXT_ONLY branches are
covered by pure unit tests in `BodyDerivationTest`. No fixture references remote `http(s)` images
either (inline `cid:` only), so the Gate-4 "blocked remote images" bar is verified by the Gate-3
sanitizer unit tests.

Used only in tests; not shipped in the application.
