package com.druvu.letterblade.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.events.DocumentEvent;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventTarget;

/**
 * Headless tests of the link guard on a real WebEngine with scripts disabled: a click inside a link is reported and
 * never navigates, a click elsewhere is ignored, the guard follows the document across reloads, and any other load is
 * cancelled and reported.
 */
public class LinkGuardTest {

    private static final String PAGE = "<html><body><p id='text'>hello</p>"
            + "<a href='https://example.invalid/track?x=1'>go <span id='inner'>now</span></a></body></html>";

    private final List<Link> clicks = new CopyOnWriteArrayList<>();
    private final List<String> escapes = new CopyOnWriteArrayList<>();
    private WebEngine engine;

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @BeforeMethod
    public void freshGuardedEngine() {
        clicks.clear();
        escapes.clear();
        engine = FxTestToolkit.call(() -> {
            WebEngine fresh = new WebView().getEngine();
            fresh.setJavaScriptEnabled(false);
            LinkGuard.install(fresh, clicks::add, escapes::add);
            return fresh;
        });
    }

    @Test
    public void clickInsideLinkIsReportedAndDoesNotNavigate() {
        load(PAGE);
        FxTestToolkit.runAndWait(() -> click("inner"));
        assertThat(clicks).containsExactly(new Link("https://example.invalid/track?x=1", "go now"));
        assertThat(FxTestToolkit.call(engine::getLocation)).isEmpty();
        assertThat(FxTestToolkit.call(() -> engine.getLoadWorker().getState())).isEqualTo(Worker.State.SUCCEEDED);
        assertThat(escapes).isEmpty();
    }

    @Test
    public void clickOutsideLinksIsIgnored() {
        load(PAGE);
        FxTestToolkit.runAndWait(() -> click("text"));
        assertThat(clicks).isEmpty();
    }

    @Test
    public void guardFollowsTheDocumentAcrossReloads() {
        load(PAGE);
        load(PAGE); // a new document: the listener must be on this one too
        FxTestToolkit.runAndWait(() -> click("inner"));
        assertThat(clicks).hasSize(1);
    }

    @Test
    public void anyOtherLoadIsCancelledAndReported() throws InterruptedException {
        load(PAGE);
        FxTestToolkit.runAndWait(() -> engine.load("https://example.invalid/elsewhere"));
        awaitEscape();
        assertThat(escapes).containsExactly("https://example.invalid/elsewhere");
        assertThat(FxTestToolkit.call(() -> engine.getLoadWorker().getState()))
                .isIn(Worker.State.CANCELLED, Worker.State.FAILED);
    }

    /** Loads {@code html} and waits for the engine to finish, so the document exists and the guard is on it. */
    private void load(String html) {
        CountDownLatch loaded = new CountDownLatch(1);
        FxTestToolkit.runAndWait(() -> {
            engine.getLoadWorker().stateProperty().addListener((obs, was, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    loaded.countDown();
                }
            });
            engine.loadContent(html);
        });
        try {
            assertThat(loaded.await(10, TimeUnit.SECONDS)).as("page loaded").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the page", e);
        }
    }

    /** A synthetic DOM click on the element, the way a mouse click reaches the page; scripts stay disabled. */
    private void click(String elementId) {
        Document document = engine.getDocument();
        Element element = document.getElementById(elementId);
        Event click = ((DocumentEvent) document).createEvent("MouseEvents");
        click.initEvent("click", true, true);
        ((EventTarget) element).dispatchEvent(click);
    }

    /** The backstop reports on a later FX pulse; give it a moment. */
    private void awaitEscape() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (escapes.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }
}
