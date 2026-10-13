package com.druvu.letterblade.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Toolbar and chip glyphs, built as inline JavaFX {@link SVGPath} nodes. No icon-font dependency, so there is nothing
 * extra to jlink or to carry as a CVE/maintenance tail - the trade the project makes for a small, fixed icon set.
 *
 * <p>Icon artwork is the duotone weight of <a href="https://phosphoricons.com">Phosphor Icons</a> (Copyright (c) 2023
 * Phosphor Icons, MIT License; see {@code NOTICE.md}), the set the druvu-wealth app uses. Each icon's two {@code <path
 * d="...">} are copied verbatim from the 256-unit viewBox: the back layer the SVG draws at 0.2 opacity, then the
 * foreground. Neither path sets a fill in code - both carry the {@value #STYLE_CLASS} style class and take their colour
 * from {@code letterblade.css}, so the glyphs follow the theme (a programmatic {@code setFill} would beat any
 * stylesheet).
 *
 * <p>A transparent viewBox-sized {@link Rectangle} sits under the paths: an {@code SVGPath}'s layout bounds are its
 * path extents, not the viewBox, so without it glyphs of one set measure differently and buttons in a row come out in
 * different sizes. A fresh node is returned on every call because a scene-graph node can only live in one place.
 */
final class Icons {

    /** Style class of every glyph path; {@code letterblade.css} owns the fill. */
    static final String STYLE_CLASS = "ph-duo";

    private static final double VIEWBOX = 256;
    private static final double SIZE = 24;
    private static final double SMALL = 16;
    private static final double BACK_LAYER_OPACITY = 0.2;
    private static final String CARET_DOWN_BACK = "M208,96l-80,80L48,96Z";
    private static final String CARET_DOWN =
            "M215.39,92.94A8,8,0,0,0,208,88H48a8,8,0,0,0-5.66,13.66l80,80a8,8,0,0,0,11.32,0l80-80A8,8,0,0,0,215.39,92.94ZM128,164.69,67.31,104H188.69Z";
    private static final String CARET_UP_BACK = "M208,160H48l80-80Z";
    private static final String CARET_UP =
            "M213.66,154.34l-80-80a8,8,0,0,0-11.32,0l-80,80A8,8,0,0,0,48,168H208a8,8,0,0,0,5.66-13.66ZM67.31,152,128,91.31,188.69,152Z";

    // --- Phosphor Icons duotone path data (MIT): back layer, then foreground ---
    private static final String FOLDER_OPEN_BACK =
            "M208,88v24H69.77a8,8,0,0,0-7.59,5.47L32,208V64a8,8,0,0,1,8-8H93.33a8,8,0,0,1,4.8,1.6L128,80h72A8,8,0,0,1,208,88Z";
    private static final String FOLDER_OPEN =
            "M245,110.64A16,16,0,0,0,232,104H216V88a16,16,0,0,0-16-16H130.67L102.94,51.2a16.14,16.14,0,0,0-9.6-3.2H40A16,16,0,0,0,24,64V208a8,8,0,0,0,8,8H211.1a8,8,0,0,0,7.59-5.47l28.49-85.47A16.05,16.05,0,0,0,245,110.64ZM93.34,64,123.2,86.4A8,8,0,0,0,128,88h72v16H69.77a16,16,0,0,0-15.18,10.94L40,158.7V64Zm112,136H43.1l26.67-80H232Z";
    private static final String LASSO_BACK =
            "M232,112c0,35.35-46.56,64-104,64S24,147.35,24,112,70.56,48,128,48,232,76.65,232,112Z";
    private static final String LASSO =
            "M205.73,59.93C184.85,47.08,157.24,40,128,40S71.15,47.08,50.27,59.93C28.17,73.52,16,92,16,112S28.17,150.44,50.27,164c19,11.67,43.49,18.56,69.73,19.73v0a37.35,37.35,0,0,1-18.58,33c-14.64,8.86-34.62,9.52-49.72,1.64a8,8,0,1,0-7.4,14.18A66.4,66.4,0,0,0,75,240a67.31,67.31,0,0,0,34.74-9.5c17-10.27,26.29-26.86,26.29-46.7v0c26.24-1.17,50.76-8.06,69.73-19.73C227.83,150.44,240,132,240,112S227.83,73.52,205.73,59.93ZM67.41,155.18c5.24-9.55,15.45-12,23.53-11,10.9,1.42,21.86,9.13,26.61,23.42C99.11,166.45,81.85,162.16,67.41,155.18Zm129.94-4.77c-16.95,10.43-39.17,16.53-63.13,17.43a54.37,54.37,0,0,0-11.39-23.07A47.17,47.17,0,0,0,93,128.35c-17-2.2-31.72,5.11-39.38,18.7C39.64,137,32,124.73,32,112c0-14.21,9.47-27.86,26.65-38.43C77.05,62.23,101.68,56,128,56S179,62.23,197.35,73.55C214.53,84.12,224,97.77,224,112S214.53,139.84,197.35,150.41Z";
    private static final String COPY_BACK = "M216,40V168H168V88H88V40Z";
    private static final String COPY =
            "M216,32H88a8,8,0,0,0-8,8V80H40a8,8,0,0,0-8,8V216a8,8,0,0,0,8,8H168a8,8,0,0,0,8-8V176h40a8,8,0,0,0,8-8V40A8,8,0,0,0,216,32ZM160,208H48V96H160Zm48-48H176V88a8,8,0,0,0-8-8H96V48H208Z";
    private static final String CODE_SIMPLE_BACK = "M240,128l-72,64H88L16,128,88,64h80Z";
    private static final String CODE_SIMPLE =
            "M93.31,70,28,128l65.27,58a8,8,0,1,1-10.62,12l-72-64a8,8,0,0,1,0-12l72-64A8,8,0,1,1,93.31,70Zm152,52-72-64a8,8,0,0,0-10.62,12L228,128l-65.27,58a8,8,0,1,0,10.62,12l72-64a8,8,0,0,0,0-12Z";
    private static final String IMAGE_BACK =
            "M224,56V178.06l-39.72-39.72a8,8,0,0,0-11.31,0L147.31,164,97.66,114.34a8,8,0,0,0-11.32,0L32,168.69V56a8,8,0,0,1,8-8H216A8,8,0,0,1,224,56Z";
    private static final String IMAGE =
            "M216,40H40A16,16,0,0,0,24,56V200a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A16,16,0,0,0,216,40Zm0,16V158.75l-26.07-26.06a16,16,0,0,0-22.63,0l-20,20-44-44a16,16,0,0,0-22.62,0L40,149.37V56ZM40,172l52-52,80,80H40Zm176,28H194.63l-36-36,20-20L216,181.38V200ZM144,100a12,12,0,1,1,12,12A12,12,0,0,1,144,100Z";
    private static final String PAPERCLIP_BACK =
            "M180.75,104.75,204,128l-82.06,81.94a48,48,0,0,1-67.88-67.88L153.37,41.37a32,32,0,0,1,45.26,45.26Z";
    private static final String PAPERCLIP =
            "M209.66,122.34a8,8,0,0,1,0,11.32l-82.05,82a56,56,0,0,1-79.2-79.21L147.67,35.73a40,40,0,1,1,56.61,56.55L105,193A24,24,0,1,1,71,159L154.3,74.38A8,8,0,1,1,165.7,85.6L82.39,170.31a8,8,0,1,0,11.27,11.36L192.93,81A24,24,0,1,0,159,47L59.76,147.68a40,40,0,1,0,56.53,56.62l82.06-82A8,8,0,0,1,209.66,122.34Z";
    private static final String ENVELOPE_SIMPLE_BACK = "M224,56l-96,88L32,56Z";
    private static final String ENVELOPE_SIMPLE =
            "M224,48H32a8,8,0,0,0-8,8V192a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A8,8,0,0,0,224,48ZM203.43,64,128,133.15,52.57,64ZM216,192H40V74.19l82.59,75.71a8,8,0,0,0,10.82,0L216,74.19V192Z";

    private Icons() {}

    /** Open a file (folder-open). */
    static Node open() {
        return duotone(FOLDER_OPEN_BACK, FOLDER_OPEN);
    }

    /** Copy to clipboard (copy). */
    static Node copy() {
        return duotone(COPY_BACK, COPY);
    }

    /** Plain-text view (code-simple). */
    static Node plainText() {
        return duotone(CODE_SIMPLE_BACK, CODE_SIMPLE);
    }

    /** Select all text (lasso). */
    static Node selectAll() {
        return duotone(LASSO_BACK, LASSO);
    }

    /** Load remote images (image). */
    static Node image() {
        return duotone(IMAGE_BACK, IMAGE);
    }

    /** File attachment (paperclip). */
    static Node paperclip() {
        return duotone(PAPERCLIP_BACK, PAPERCLIP);
    }

    /** Embedded message (envelope-simple). */
    static Node envelope() {
        return duotone(ENVELOPE_SIMPLE_BACK, ENVELOPE_SIMPLE);
    }

    /** Expand the recipient rows (caret-down, small). */
    static Node caretDown() {
        return duotone(CARET_DOWN_BACK, CARET_DOWN, SMALL);
    }

    /** Collapse the recipient rows (caret-up, small). */
    static Node caretUp() {
        return duotone(CARET_UP_BACK, CARET_UP, SMALL);
    }

    private static Node duotone(String backLayer, String foreground) {
        return duotone(backLayer, foreground, SIZE);
    }

    private static Node duotone(String backLayer, String foreground, double size) {
        final Rectangle frame = new Rectangle(VIEWBOX, VIEWBOX, Color.TRANSPARENT);
        final SVGPath back = path(backLayer);
        back.setOpacity(BACK_LAYER_OPACITY);
        final Group artwork = new Group(frame, back, path(foreground));
        final double factor = size / VIEWBOX;
        // A transform, not setScaleX/Y: layout bounds ignore a node's own scale, so the wrapping
        // Group below is what makes layout see SIZE x SIZE instead of the 256-unit frame.
        artwork.getTransforms().add(new Scale(factor, factor));
        return new Group(artwork);
    }

    private static SVGPath path(String content) {
        final SVGPath path = new SVGPath();
        path.setContent(content);
        path.getStyleClass().add(STYLE_CLASS);
        return path;
    }
}
