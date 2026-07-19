package com.druvu.letterblade.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.letterblade.msg.BodyBranch;
import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.status.StatusBarModel;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Headless UI tests for {@link MainView}: they build the real view on the FX thread (no window shown) and assert
 * scene-graph state through stable node ids. Fixtures are the same real {@code .msg} files the parser tests use.
 */
public class MainViewTest {

    private final MsgService service = new MsgService();

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    // --- attachment / embedded chips ---

    @Test
    public void fileAttachmentsProduceVisibleChips() throws Exception {
        MainView view = show(service.parse(fixture("html-with-attachment-and-embedded-image.msg")));
        assertThat(FxTestToolkit.call(() -> chipRow(view).isVisible())).isTrue();
        assertThat(FxTestToolkit.call(() -> chipRow(view).getChildren().size())).isEqualTo(2);
    }

    @Test
    public void nullFilenameAttachmentsStillProduceChips() throws Exception {
        MainView view = show(service.parse(fixture("attachments.msg")));
        assertThat(FxTestToolkit.call(() -> chipRow(view).getChildren().size())).isEqualTo(2);
        String firstLabel =
                FxTestToolkit.call(() -> ((Button) chipRow(view).getChildren().get(0)).getText());
        assertThat(firstLabel).startsWith("attachment");
    }

    @Test
    public void embeddedMessageProducesChip() throws Exception {
        MainView view = show(service.parse(fixture("nested-simple-mail.msg")));
        assertThat(FxTestToolkit.call(() -> chipRow(view).isVisible())).isTrue();
        assertThat(FxTestToolkit.call(() -> chipRow(view).getChildren().size())).isEqualTo(1);
    }

    @Test
    public void noAttachmentsHidesChipRow() throws Exception {
        MainView view = show(service.parse(fixture("rtf-sample-email.msg")));
        assertThat(FxTestToolkit.call(() -> chipRow(view).isVisible())).isFalse();
        assertThat(FxTestToolkit.call(() -> chipRow(view).isManaged())).isFalse();
    }

    // --- header / body edge cases (Gate 6) ---

    @Test
    public void longSubjectEllipsizesWithTooltip() {
        String subject = "A very long subject line ".repeat(12).trim();
        MainView view = show(message(subject, "<p>hi</p>", "hi", BodyBranch.HTML, "Alice <a@x>", ""));
        Label label = FxTestToolkit.call(() -> (Label) view.node().lookup("#subject"));
        assertThat(FxTestToolkit.call(label::isWrapText)).isFalse();
        assertThat(FxTestToolkit.call(() -> label.getTooltip().getText())).isEqualTo(subject);
    }

    @Test
    public void emptyBodyShowsPlaceholder() {
        MainView view = show(message("No body here", null, "", BodyBranch.TEXT_ONLY, "Alice <a@x>", ""));
        Label placeholder = FxTestToolkit.call(() -> (Label) view.node().lookup("#bodyPlaceholder"));
        assertThat(FxTestToolkit.call(placeholder::isVisible)).isTrue();
        assertThat(FxTestToolkit.call(placeholder::getText)).isEqualTo("(no message body)");
    }

    @Test
    public void blankRecipientRowsAreHidden() {
        MainView view = show(message("Subject", "<p>hi</p>", "hi", BodyBranch.HTML, "Alice <a@x>", ""));
        assertThat(FxTestToolkit.call(() -> row(view, "#toRow").isManaged())).isTrue();
        assertThat(FxTestToolkit.call(() -> row(view, "#ccRow").isManaged())).isFalse();
    }

    @Test
    public void openingAnEmptyBodyMessageClearsAStaleBlockedBar() {
        MainView view = show(message(
                "Has remote images", "<img src='http://example.com/x.png'>", "x", BodyBranch.HTML, "Alice <a@x>", ""));
        assertThat(FxTestToolkit.call(() -> row(view, "#blockedBar").isManaged()))
                .isTrue();

        // Reusing the same view (as an "open another file" does), show a body-less message.
        FxTestToolkit.runAndWait(
                () -> view.showMessage(message("Empty", null, "", BodyBranch.TEXT_ONLY, "Bob <b@x>", "")));
        assertThat(FxTestToolkit.call(() -> row(view, "#blockedBar").isManaged()))
                .isFalse();
    }

    // --- helpers ---

    private MainView show(ParsedMessage message) {
        MainView view = FxTestToolkit.call(this::newView);
        FxTestToolkit.runAndWait(() -> view.showMessage(message));
        FxTestToolkit.runAndWait(() -> view.node().applyCss());
        return view;
    }

    private MainView newView() {
        FxBus bus = new FxBus();
        FxExec exec = new FxExec(bus);
        MainView view = new MainView(
                exec,
                service,
                new Sanitizer(),
                new Notifications(new Stage()),
                new StatusBarModel(bus),
                AppHome.of("letterblade"),
                s -> {},
                m -> {});
        new Scene(view.node()); // attach to a scene so #id lookups resolve; no window is shown
        return view;
    }

    private static FlowPane chipRow(MainView view) {
        return (FlowPane) view.node().lookup("#attachmentsRow");
    }

    private static Region row(MainView view, String id) {
        return (Region) view.node().lookup(id);
    }

    private static ParsedMessage message(
            String subject, String bodyHtml, String plainText, BodyBranch branch, String to, String cc) {
        return new ParsedMessage(
                subject,
                "Sender",
                "sender@example.com",
                to,
                cc,
                Instant.parse("2026-07-19T10:15:30Z"),
                bodyHtml,
                plainText,
                branch,
                List.of(),
                List.of(),
                Map.of());
    }

    private File fixture(String name) throws Exception {
        return new File(getClass().getResource("/test-messages/" + name).toURI());
    }
}
