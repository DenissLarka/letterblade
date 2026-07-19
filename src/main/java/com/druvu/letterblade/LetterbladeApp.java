package com.druvu.letterblade;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.render.Sanitizer;
import com.druvu.letterblade.ui.MainView;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.prefs.WindowGeometry;
import com.druvu.lib.fx.status.StatusBarModel;
import com.druvu.lib.fx.util.FxThreads;
import java.io.File;
import java.util.List;
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
    private final Prefs prefs = Prefs.in(AppHome.of("letterblade"));
    private final MsgService msgService = new MsgService();
    private final Sanitizer sanitizer = new Sanitizer();

    private StatusBarModel statusBarModel;
    private Notifications notifications;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        FxThreads.requireFx();
        notifications = new Notifications(stage);
        statusBarModel = new StatusBarModel(bus);

        final MainView view = new MainView(exec, msgService, sanitizer, notifications, statusBarModel, stage::setTitle);
        final Scene scene = new Scene(view.node(), 900, 640);
        view.installDragAndDrop(scene);

        stage.setTitle("Letterblade");
        stage.setScene(scene);
        // Restore the window's saved position/size (and save it again when the app closes).
        WindowGeometry.install(stage, prefs);
        stage.show();

        // File association / "open with" hands the path as the first CLI argument (Windows/Linux).
        final List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            view.openFile(new File(args.get(0)));
        }
    }

    @Override
    public void stop() {
        if (notifications != null) {
            notifications.close();
        }
        if (statusBarModel != null) {
            statusBarModel.close();
        }
        exec.close();
    }
}
