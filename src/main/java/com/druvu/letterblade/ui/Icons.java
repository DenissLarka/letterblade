package com.druvu.letterblade.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Toolbar and chip glyphs, built as inline JavaFX {@link SVGPath} nodes in the druvu-lib-fx two-tone style (light fill,
 * blue edge). No icon-font dependency, so there is nothing extra to jlink or to carry as a CVE/maintenance tail - the
 * trade the project makes for a small, fixed icon set.
 *
 * <p>Icon artwork is from <a href="https://github.com/twbs/icons">Bootstrap Icons</a> (Copyright (c) 2019-2024 The
 * Bootstrap Authors, MIT License; see {@code NOTICE.md}). Each icon's {@code <path d="...">} is copied verbatim;
 * Bootstrap Icons use a 16-unit viewBox. A fresh node is returned on every call because a scene-graph node can only
 * live in one place.
 */
final class Icons {

    private static final Color FILL = Color.web("#d7e0ff");
    private static final Color EDGE = Color.web("#4147d5");
    private static final double STROKE = 0.45; // in the 16-unit viewBox
    private static final double VIEWBOX = 16;
    private static final double SIZE = 24;

    // --- Bootstrap Icons path data (MIT) ---
    private static final String FOLDER2_OPEN =
            "M1 3.5A1.5 1.5 0 0 1 2.5 2h2.764c.958 0 1.76.56 2.311 1.184C7.985 3.648 8.48 4 9 4h4.5A1.5 1.5 0 0 1 15 5.5v.64c.57.265.94.876.856 1.546l-.64 5.124A2.5 2.5 0 0 1 12.733 15H3.266a2.5 2.5 0 0 1-2.481-2.19l-.64-5.124A1.5 1.5 0 0 1 1 6.14zM2 6h12v-.5a.5.5 0 0 0-.5-.5H9c-.964 0-1.71-.629-2.174-1.154C6.374 3.334 5.82 3 5.264 3H2.5a.5.5 0 0 0-.5.5zm-.367 1a.5.5 0 0 0-.496.562l.64 5.124A1.5 1.5 0 0 0 3.266 14h9.468a1.5 1.5 0 0 0 1.489-1.314l.64-5.124A.5.5 0 0 0 14.367 7z";
    private static final String CLIPBOARD_A =
            "M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1z";
    private static final String CLIPBOARD_B =
            "M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0z";
    private static final String CARD_TEXT_A =
            "M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z";
    private static final String CARD_TEXT_B =
            "M3 5.5a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5M3 8a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9A.5.5 0 0 1 3 8m0 2.5a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 0 1h-6a.5.5 0 0 1-.5-.5";
    private static final String CHECK_ALL =
            "M8.97 4.97a.75.75 0 0 1 1.07 1.05l-3.99 4.99a.75.75 0 0 1-1.08.02L2.324 8.384a.75.75 0 1 1 1.06-1.06l2.094 2.093L8.95 4.992zm-.92 5.14.92.92a.75.75 0 0 0 1.079-.02l3.992-4.99a.75.75 0 1 0-1.091-1.028L9.477 9.417l-.485-.486z";
    private static final String IMAGE_A = "M6.002 5.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0";
    private static final String IMAGE_B =
            "M2.002 1a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V3a2 2 0 0 0-2-2zm12 1a1 1 0 0 1 1 1v6.5l-3.777-1.947a.5.5 0 0 0-.577.093l-3.71 3.71-2.66-1.772a.5.5 0 0 0-.63.062L1.002 12V3a1 1 0 0 1 1-1z";
    private static final String PAPERCLIP =
            "M4.5 3a2.5 2.5 0 0 1 5 0v9a1.5 1.5 0 0 1-3 0V5a.5.5 0 0 1 1 0v7a.5.5 0 0 0 1 0V3a1.5 1.5 0 1 0-3 0v9a2.5 2.5 0 0 0 5 0V5a.5.5 0 0 1 1 0v7a3.5 3.5 0 1 1-7 0z";
    private static final String ENVELOPE =
            "M0 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2zm2-1a1 1 0 0 0-1 1v.217l7 4.2 7-4.2V4a1 1 0 0 0-1-1zm13 2.383-4.708 2.825L15 11.105zm-.034 6.876-5.64-3.471L8 9.583l-1.326-.795-5.64 3.47A1 1 0 0 0 2 13h12a1 1 0 0 0 .966-.741M1 11.105l4.708-2.897L1 5.383z";

    private Icons() {}

    /** Open a file (folder2-open). */
    static Node open() {
        return glyph(FillRule.NON_ZERO, FOLDER2_OPEN);
    }

    /** Copy to clipboard (clipboard). */
    static Node copy() {
        return glyph(FillRule.NON_ZERO, CLIPBOARD_A, CLIPBOARD_B);
    }

    /** Plain-text view (card-text). */
    static Node plainText() {
        return glyph(FillRule.NON_ZERO, CARD_TEXT_A, CARD_TEXT_B);
    }

    /** Select all text (check-all). */
    static Node selectAll() {
        return glyph(FillRule.NON_ZERO, CHECK_ALL);
    }

    /** Load remote images (image). */
    static Node image() {
        return glyph(FillRule.NON_ZERO, IMAGE_A, IMAGE_B);
    }

    /** File attachment (paperclip). */
    static Node paperclip() {
        return glyph(FillRule.NON_ZERO, PAPERCLIP);
    }

    /** Embedded message (envelope). */
    static Node envelope() {
        return glyph(FillRule.NON_ZERO, ENVELOPE);
    }

    private static Node glyph(FillRule rule, String... paths) {
        final Group group = new Group();
        for (String content : paths) {
            final SVGPath path = new SVGPath();
            path.setContent(content);
            path.setFillRule(rule);
            path.setFill(FILL);
            path.setStroke(EDGE);
            path.setStrokeWidth(STROKE);
            path.setStrokeLineJoin(StrokeLineJoin.ROUND);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            group.getChildren().add(path);
        }
        group.setScaleX(SIZE / VIEWBOX);
        group.setScaleY(SIZE / VIEWBOX);
        return group;
    }
}
