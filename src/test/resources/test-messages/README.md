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

`sailing-club-newsletter.msg` is ours, generated 2026-10-20 by `src/test/fixtures/NewsletterMsg.java`
(a JBang script writing the MS-OXMSG layout with Apache POI): an HTML newsletter with a native HTML
body, one inline `cid:` banner photo, a PDF and an `.ics` attachment. It is also the README and
druvu.com hero message. The photo is "Sunset Key and Sailboats, Florida, 2025" by Julian Lupyan,
CC0, from Wikimedia Commons, cropped to 1440×576.

Note: no upstream fixture exposes a native `getBodyHTML()` — every Outlook `.msg` stores its body as
RTF, so those four take the `RTF_CONVERTED` branch; the generated newsletter carries `PR_HTML` and
takes the native-HTML branch. The TEXT_ONLY branch is covered by pure unit tests in
`BodyDerivationTest`. No fixture references remote `http(s)` images (inline `cid:` only), so the
Gate-4 "blocked remote images" bar is verified by the Gate-3 sanitizer unit tests.

Used only in tests; not shipped in the application.
