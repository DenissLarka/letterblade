package com.druvu.letterblade;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer;
import com.druvu.letterblade.ui.MainView;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.os.DesktopHooks;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.prefs.WindowGeometry;
import com.druvu.lib.fx.status.StatusBarModel;
import com.druvu.lib.fx.theme.ThemeManager;
import com.druvu.lib.fx.util.FxThreads;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Letterblade - the JavaFX application entry point.
 *
 * <p>Deliberately thin: it owns the JavaFX {@link Application}/{@link Stage}/{@link Scene} lifecycle and the single
 * shared toolkit wiring (one {@link FxBus} feeding {@link FxExec} and the {@link StatusBarModel}), constructs the
 * collaborators, then delegates all UI and behaviour to {@link MainView}. It also opens a file passed on the command
 * line (how file associations invoke the app on Windows/Linux).
 */
public final class LetterbladeApp extends Application {

    /** One bus shared by exec and the status model - the druvu-lib-fx wiring contract. */
    private final FxBus bus = new FxBus();

    private final FxExec exec = new FxExec(bus);
    private final AppHome home = AppHome.of("letterblade");
    private final Prefs prefs = Prefs.in(home);
    private final MsgService msgService = new MsgService();
    private final Sanitizer sanitizer = new Sanitizer();
    private final ThemeManager themeManager = new ThemeManager(prefs);

    private StatusBarModel statusBarModel;
    private MainView primaryView;

    /** Set by {@link #start}; the desktop hooks are registered before any instance exists (see {@link #main}). */
    private static final AtomicReference<LetterbladeApp> RUNNING = new AtomicReference<>();

    public static void main(String[] args) {
        installDesktopHooks();
        launch(args);
    }

    /**
     * Wires the macOS application menu and Finder's open-file event. These handlers are process-global - the JDK keeps
     * one per event, not a list - so they belong here rather than in {@link MainView}, which exists per window.
     *
     * <p><b>Registered before {@code launch()}, and that ordering is load-bearing.</b> On macOS the application menu
     * belongs to whoever builds it first: register here and AWT installs its About entry; register in {@code start()}
     * and JavaFX's Glass has already built a bare Hide/Quit menu, after which the About item never appears even though
     * registration reports success. Measured both ways on JavaFX 25 - see {@code DesktopHooks}.
     *
     * <p>Only About and open-file are registered. There is no Settings screen to open and nothing unsaved to protect on
     * quit, so registering those would only add application-menu entries that do nothing.
     */
    private static void installDesktopHooks() {
        // Registered for the day it works (and it costs nothing), but do NOT rely on it: measured on JavaFX 25, AWT's
        // About entry never appears in a JavaFX app's application menu. The Help menu carries the reachable About.
        DesktopHooks.onAbout(() -> onRunningApp(app -> app.primaryView.showAbout()));
        // The macOS counterpart of the CLI argument in start(): Finder double-click, Dock drop, or "Open With". Note
        // macOS only sends this to a real .app bundle, so it first becomes exercisable at the dist phase.
        DesktopHooks.onOpenFiles(paths -> onRunningApp(app -> app.openFiles(paths)));
    }

    /** Runs an action against the started app, or drops it if the UI is not up yet. */
    private static void onRunningApp(Consumer<LetterbladeApp> action) {
        final LetterbladeApp app = RUNNING.get();
        if (app != null && app.primaryView != null) {
            action.accept(app);
        }
    }

    /**
     * The first file replaces this window's content (same as File &gt; Open); any others get their own window rather
     * than overwriting each other.
     */
    private void openFiles(List<Path> paths) {
        primaryView.openFile(paths.getFirst().toFile());
        paths.stream().skip(1).forEach(path -> openFileInNewWindow(path.toFile()));
    }

    @Override
    public void start(Stage stage) {
        FxThreads.requireFx();
        // Theme first, before any toolkit widget exists. Toolkit widgets style themselves from AtlantaFX colour
        // variables, and a JavaFX lookup that does not resolve is not a fallback - the declaration is dropped. Under
        // the default Modena stylesheet that left every toast with no background and no text colour, which is what
        // the toolkit's "created before any theme was applied" warning was reporting at each launch.
        themeManager.applyStored();
        statusBarModel = new StatusBarModel(bus);

        final MainView view = newView(stage);
        final Scene scene = new Scene(view.node(), 900, 640);
        view.installDragAndDrop(scene);

        stage.setTitle("Letterblade");
        stage.setScene(scene);
        // Restore the window's saved position/size (and save it again when the app closes).
        WindowGeometry.install(stage, prefs);

        // The hooks were registered in main(); hand them a live app to talk to.
        this.primaryView = view;
        RUNNING.set(this);
        stage.show();

        // File association / "open with" hands the path as the first CLI argument (Windows/Linux).
        final List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            view.openFile(new File(args.get(0)));
        }
    }

    /** Opens a file in a fresh window - used when the OS hands over several documents at once. */
    private void openFileInNewWindow(File file) {
        final Stage stage = new Stage();
        final MainView view = newView(stage);
        final Scene scene = new Scene(view.node(), 900, 640);
        view.installDragAndDrop(scene);
        stage.setScene(scene);
        stage.show();
        view.openFile(file);
    }

    /**
     * Builds a viewer wired to the shared collaborators, its title bound to {@code stage} and able to spawn siblings.
     * Each window gets its own {@link Notifications} bound to its own stage - so toasts anchor to the window that
     * produced them - and it is closed when the window hides.
     */
    private MainView newView(Stage stage) {
        final Notifications windowNotifications = new Notifications(stage);
        stage.setOnHidden(event -> windowNotifications.close());
        return new MainView(
                exec,
                msgService,
                sanitizer,
                windowNotifications,
                statusBarModel,
                home,
                stage::setTitle,
                this::openInNewWindow);
    }

    /**
     * Opens an already-parsed message (an embedded {@code .msg}) in its own window, reusing the full viewer component
     * so a nested message behaves exactly like a top-level one - including its own attachments and any further nesting.
     */
    private void openInNewWindow(ParsedMessage message) {
        final Stage stage = new Stage();
        final MainView view = newView(stage);
        final Scene scene = new Scene(view.node(), 900, 640);
        view.installDragAndDrop(scene);
        stage.setScene(scene);
        view.showMessage(message);
        stage.show();
    }

    @Override
    public void stop() {
        // Each window's Notifications is closed by its own stage's onHidden handler (see newView).
        if (statusBarModel != null) {
            statusBarModel.close();
        }
        exec.close();
    }
}
