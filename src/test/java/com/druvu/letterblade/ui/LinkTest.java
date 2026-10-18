package com.druvu.letterblade.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URISyntaxException;
import org.testng.annotations.Test;

/**
 * Unit tests for the JavaFX-free link rules: text normalisation, the "shown as" row, mailto wording, the URI hand-off.
 */
public class LinkTest {

    @Test
    public void collapsesWhitespaceAndStrips() {
        Link link = new Link("  https://example.com/a  ", " Read\n\t more  ");
        assertThat(link.address()).isEqualTo("https://example.com/a");
        assertThat(link.text()).isEqualTo("Read more");
    }

    @Test
    public void nullsBecomeEmpty() {
        Link link = new Link(null, null);
        assertThat(link.address()).isEmpty();
        assertThat(link.text()).isEmpty();
        assertThat(link.textDiffers()).isFalse();
    }

    @Test
    public void textDiffersOnlyWhenItAddsSomething() {
        assertThat(new Link("https://example.com", "https://example.com").textDiffers())
                .isFalse();
        assertThat(new Link("https://example.com", "").textDiffers()).isFalse();
        assertThat(new Link("https://example.com", "Read more").textDiffers()).isTrue();
        assertThat(new Link("https://evil.example", "www.bank.example").textDiffers())
                .isTrue();
    }

    @Test
    public void displayTextIsCutWithAnEllipsis() {
        assertThat(new Link("https://example.com", "x".repeat(200)).displayText())
                .hasSize(161)
                .endsWith("…");
        assertThat(new Link("https://example.com", "short").displayText()).isEqualTo("short");
    }

    @Test
    public void mailLinksOpenInTheMailApp() {
        assertThat(new Link("MAILTO:someone@example.com", "").isMail()).isTrue();
        assertThat(new Link("mailto:someone@example.com", "").opensIn()).isEqualTo("your mail app");
        assertThat(new Link("https://example.com", "").isMail()).isFalse();
        assertThat(new Link("https://example.com", "").opensIn()).isEqualTo("your browser");
    }

    @Test
    public void uriEncodesSpacesAndRefusesTheRest() throws URISyntaxException {
        assertThat(new Link("https://example.com/a b", "").uri().toString()).isEqualTo("https://example.com/a%20b");
        assertThatThrownBy(() -> new Link("https://example.com/{a}", "").uri()).isInstanceOf(URISyntaxException.class);
    }
}
