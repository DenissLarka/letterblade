package com.druvu.letterblade;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer;
import com.druvu.letterblade.ui.AppIcon;
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
import javafx.scene.image.Image;
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
    private final List<Image> windowIcons = AppIcon.images();

    private StatusBarModel statusBarModel;
    private MainView primaryView;

    /** Set by {@link #start}; the About hook is registered before any instance exists (see {@link #main}). */
    private static final AtomicReference<LetterbladeApp> RUNNING = new AtomicReference<>();

    public static void main(String[] args) {
        installDesktopHooks();
        launch(args);
    }

    /**
     * Wires the macOS application menu. The handler is process-global - the JDK keeps one per event, not a list - so it
     * belongs here rather than in {@link MainView}, which exists per window.
     *
     * <p><b>Registered before {@code launch()}, and that ordering is load-bearing.</b> It brings AWT up before JavaFX,
     * which makes AWT the receiver of the application events macOS sends - including the open-file event that
     * {@link #start} subscribes to. Up to JavaFX 25 the same ordering also put AWT's About entry into the application
     * menu; see {@code DesktopHooks}.
     *
     * <p>There is no Settings screen to open and nothing unsaved to protect on quit, so those hooks are not registered.
     */
    private static void installDesktopHooks() {
        // Shown only up to JavaFX 25: since JavaFX 26 (JDK-8359108) Glass builds the application menu and AWT's About
        // entry no longer appears there. The Help menu carries the reachable About.
        DesktopHooks.onAbout(() -> onRunningApp(app -> app.primaryView.showAbout()));
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

        // The About hook was registered in main(); hand it a live app to talk to.
        this.primaryView = view;
        RUNNING.set(this);
        stage.show();

        // Finder double-click, Dock drop or "Open With" (macOS sends these only to a .app bundle). Subscribed once a
        // window exists: AWT holds an event that arrived during launch until a handler is in place, so the document
        // that launched the app arrives here too, instead of reaching an app with no window to show it in.
        DesktopHooks.onOpenFiles(this::openFiles);

        // File association / "open with" hands the path as the first CLI argument (Windows/Linux).
        final List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            view.openFile(new File(unquoted(args.get(0))));
        }
    }

    /**
     * A dev-run path can arrive in quotes: the javafx-maven-plugin groups a path with spaces by its quotes and keeps
     * them on the way to the app (measured with 0.0.8 on macOS, where a bare path is split at its spaces instead). A
     * real launcher never sends quotes, and a file name that starts and ends with one is not a case worth keeping.
     */
    static String unquoted(String arg) {
        final boolean quoted = arg.length() >= 2 && arg.startsWith("\"") && arg.endsWith("\"");
        return quoted ? arg.substring(1, arg.length() - 1) : arg;
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
     * produced them - and it is closed when the window hides. Every window passes through here, so this is also where
     * it gets the app icon.
     */
    private MainView newView(Stage stage) {
        stage.getIcons().setAll(windowIcons);
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
