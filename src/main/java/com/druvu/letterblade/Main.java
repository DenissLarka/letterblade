package com.druvu.letterblade;

/**
 * Letterblade's launch class: the main class of every installer and of the development run.
 *
 * <p>Deliberately not a JavaFX {@link javafx.application.Application}. The Java launcher hands an Application subclass
 * to JavaFX, which starts its toolkit before that class's {@code main} runs. AWT must start first on macOS, or the
 * application events go to JavaFX and every file opened from Finder, the Dock or "Open With" is lost. This class keeps
 * the launcher on its plain path, so {@link LetterbladeApp#launchWithDesktopHooks} runs before JavaFX exists.
 *
 * @author Deniss Larka <br>
 *     on 25 Nov 2026
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        LetterbladeApp.launchWithDesktopHooks(args);
    }
}
