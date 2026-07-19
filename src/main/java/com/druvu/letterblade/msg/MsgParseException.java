package com.druvu.letterblade.msg;

import java.io.Serial;

/**
 * Thrown when a file cannot be read as an Outlook {@code .msg} message (corrupt, wrong format, or otherwise
 * unreadable). Checked on purpose: the UI is expected to catch it and show a friendly error rather than crash, since
 * the app's whole job is opening arbitrary, possibly hostile files.
 */
public class MsgParseException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public MsgParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
