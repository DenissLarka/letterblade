package com.druvu.letterblade.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Covers the span-to-image swap: a decodable PNG per run, the run kept as alt, untouched input left alone. */
public class EmojiImagesTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void emojiSpanBecomesAnInlineImageWithTheRunAsAlt() throws Exception {
        EmojiImages images = FxTestToolkit.call(EmojiImages::new);
        if (!images.available()) {
            throw new SkipException("no emoji font installed on this machine");
        }
        String html = FxTestToolkit.call(() ->
                images.inline("<p>see <span class=\"lb-emoji\" style=\"font-family:x\">&#x1f4ca;</span> now</p>"));

        assertThat(html)
                .doesNotContain("lb-emoji")
                .contains("alt=\"&#x1f4ca;\"")
                .contains("see ")
                .contains(" now");
        String base64 = html.replaceAll("(?s).*src=\"data:image/png;base64,([^\"]+)\".*", "$1");
        BufferedImage png =
                ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertThat(png).as("the data URI decodes as a PNG").isNotNull();
        assertThat(png.getWidth()).isGreaterThan(0);
        assertThat(png.getHeight()).isGreaterThan(0);
    }

    @Test
    public void htmlWithoutEmojiSpansIsReturnedAsIs() {
        EmojiImages images = FxTestToolkit.call(EmojiImages::new);
        String html = "<p>plain &amp; simple</p>";
        assertThat(FxTestToolkit.call(() -> images.inline(html))).isSameAs(html);
    }
}
