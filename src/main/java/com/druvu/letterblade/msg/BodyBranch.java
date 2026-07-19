package com.druvu.letterblade.msg;

/**
 * Which source produced the message body that will be rendered, and how the Rendered/Plain view should default. Outlook
 * usually stores the body as RTF, so {@link #RTF_CONVERTED} is the common case; a native HTML body ({@link #HTML}) or a
 * plain-text-only message ({@link #TEXT_ONLY}) also occur.
 */
public enum BodyBranch {

    /** {@code getBodyHTML()} was present: render that HTML directly. */
    HTML,

    /** No native HTML; render the library's RTF-to-HTML conversion ({@code getConvertedBodyHTML()}). */
    RTF_CONVERTED,

    /** No HTML at all; only plain text is available (rendered as HTML-escaped {@code <pre>} text). */
    TEXT_ONLY
}
