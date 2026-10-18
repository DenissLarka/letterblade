package com.druvu.letterblade.ui;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

/**
 * Keeps the message's WebView on the message. A click on a link is stopped before the engine starts navigating and
 * handed over as a {@link Link}, so the view can show the reader where it leads and open it outside; and should the
 * engine still start loading anything else, by a path this guard did not foresee, the load is cancelled and the view is
 * told to put the message back.
 *
 * <p>This works with JavaScript disabled: the click listener is a Java-side DOM listener, which WebKit dispatches
 * without the script engine, and {@code preventDefault()} from it cancels the navigation before any request leaves the
 * machine (measured on JavaFX 27). Content shown with {@code loadContent} has an empty location, so a non-empty
 * location means the engine is heading somewhere else.
 *
 * @author Deniss Larka <br>
 *     on 18 Oct 2026
 */
final class LinkGuard {

    private final WebEngine engine;
    private final Consumer<Link> onLinkClick;
    private final Consumer<String> onEscape;

    /**
     * A field on purpose: WebKit holds Java DOM listeners weakly, so a listener nobody else references is collected.
     */
    private final EventListener clickListener = this::onClick;

    private LinkGuard(WebEngine engine, Consumer<Link> onLinkClick, Consumer<String> onEscape) {
        this.engine = engine;
        this.onLinkClick = onLinkClick;
        this.onEscape = onEscape;
    }

    /**
     * Guards {@code engine} for the rest of its life: {@code onLinkClick} gets every link the reader clicks, instead of
     * the engine navigating; {@code onEscape} gets the location of any other load the engine started, after the guard
     * has cancelled it, and is expected to reload the message.
     */
    static void install(WebEngine engine, Consumer<Link> onLinkClick, Consumer<String> onEscape) {
        final LinkGuard guard = new LinkGuard(
                Objects.requireNonNull(engine, "engine"),
                Objects.requireNonNull(onLinkClick, "onLinkClick"),
                Objects.requireNonNull(onEscape, "onEscape"));
        // Every loadContent builds a new document, so the click listener goes on each one as it arrives.
        engine.getLoadWorker().stateProperty().addListener((obs, was, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                guard.listenForClicks();
            }
        });
        engine.locationProperty().addListener((obs, was, location) -> guard.escapeCheck(location));
    }

    private void listenForClicks() {
        if (engine.getDocument() instanceof EventTarget document) {
            // Capture phase at the document: the click is seen before anything in the page, whatever it hit.
            document.addEventListener("click", clickListener, true);
        }
    }

    private void onClick(Event event) {
        final Element link = enclosingLink(event.getTarget());
        if (link == null) {
            return; // a click on text or an image: nothing to guard
        }
        event.preventDefault(); // the navigation never starts
        onLinkClick.accept(new Link(link.getAttribute("href"), link.getTextContent()));
    }

    /** The backstop: any location but the empty one of our own content means the engine is leaving the message. */
    private void escapeCheck(String location) {
        if (location == null || location.isEmpty()) {
            return;
        }
        engine.getLoadWorker().cancel();
        // Deferred: the view reloads the message, and loading from inside a location change would nest the change.
        Platform.runLater(() -> onEscape.accept(location));
    }

    /** The link a click landed in: the nearest {@code <a href>} from the click target upwards, or null. */
    private static Element enclosingLink(Object target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null) {
            if (node instanceof Element element
                    && "a".equalsIgnoreCase(element.getTagName())
                    && element.hasAttribute("href")) {
                return element;
            }
            node = node.getParentNode();
        }
        return null;
    }
}
