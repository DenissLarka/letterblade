package com.druvu.letterblade.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Replaces the sanitizer's emoji spans with inline images drawn by JavaFX's own text renderer.
 *
 * <p>Why images: the JavaFX WebKit port cannot lay out supplementary-plane characters - each surrogate pair comes out
 * as the glyph plus a stray missing-glyph box with no advance, painted over the next letter (measured on JavaFX 27).
 * JavaFX's {@code Text} node draws the same characters cleanly, so every emoji run is rendered once to a transparent
 * PNG at a size above the body text and scaled to the text's em in CSS. Monochrome where the platform's emoji font is
 * drawn without colour layers; colour where JavaFX supports it (macOS).
 *
 * <p>This runs <em>after</em> the sanitizer, the security boundary, and only exchanges the spans the sanitizer itself
 * created for {@code <img>} elements whose bytes were generated here; nothing from the message becomes markup. Must be
 * called on the FX thread - snapshots require it. Runs are cached, so a message repeating one emoji costs one render.
 *
 * @author Deniss Larka <br>
 *     on 13 Oct 2026
 */
final class EmojiImages {

    /** The class the sanitizer puts on an emoji run. */
    static final String SPAN_CLASS = "lb-emoji";

    /** Emoji families by platform; the first installed one draws. */
    private static final List<String> FAMILIES =
            List.of("Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", "Noto Emoji", "Symbola");

    /** Render size in px: well above body text, so the scaled-down image stays crisp on high-DPI screens. */
    private static final double RENDER_SIZE = 40;

    /** The image's height in the text's em and the baseline shift that matches a Text node's descent. */
    private static final String IMG_STYLE = "height:1.25em;vertical-align:-0.25em";

    private final String family;
    private final Map<String, String> cache = new HashMap<>();

    EmojiImages() {
        final List<String> installed = Font.getFamilies();
        family = FAMILIES.stream().filter(installed::contains).findFirst().orElse(null);
    }

    /** False when no emoji family is installed - {@link #inline} then returns its input unchanged. */
    boolean available() {
        return family != null;
    }

    /**
     * Swaps every {@code span.lb-emoji} in sanitized HTML for a generated {@code <img>}; the run's characters stay as
     * the image's {@code alt}. Serialized as ASCII again, like the sanitizer's output.
     */
    String inline(String safeHtml) {
        if (family == null || safeHtml == null || !safeHtml.contains(SPAN_CLASS)) {
            return safeHtml;
        }
        final Document doc = Jsoup.parseBodyFragment(safeHtml);
        for (Element span : doc.select("span." + SPAN_CLASS)) {
            final String run = span.text();
            final String dataUri = cache.computeIfAbsent(run, this::render);
            if (dataUri == null) {
                continue; // rendering failed: the span and its font chain remain
            }
            final Element img = doc.createElement("img");
            img.attr("alt", run).attr("src", dataUri).attr("style", IMG_STYLE);
            span.replaceWith(img);
        }
        doc.outputSettings().charset(StandardCharsets.US_ASCII);
        return doc.body().html();
    }

    private String render(String run) {
        final Text text = new Text(run);
        text.setFont(Font.font(family, RENDER_SIZE));
        text.setFill(Color.BLACK);
        final SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        final WritableImage image = text.snapshot(params, null);
        final int width = (int) Math.ceil(image.getWidth());
        final int height = (int) Math.ceil(image.getHeight());
        if (width <= 0 || height <= 0) {
            return null;
        }
        final int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        final BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, width, height, argb, 0, width);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(png, "png", bytes);
        } catch (IOException e) {
            return null;
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
