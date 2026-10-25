package com.druvu.letterblade.ui;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;

/**
 * The app icon in window title bars, Alt-Tab and the taskbar.
 *
 * <p>Windows and Linux take a window's icon from the window itself, not from the installed package, so every window
 * sets it; a dialog created with an owner takes the owner's. One large source image is scaled to the sizes those places
 * choose from, at 100% to 200% display scaling. macOS takes its icons from the app bundle instead.
 *
 * @author Deniss Larka <br>
 *     on 24 Oct 2026
 */
public final class AppIcon {

    private static final String SOURCE = "app-icon.png";
    private static final List<Integer> SIZES = List.of(16, 20, 24, 32, 40, 48, 64, 128, 256);

    private AppIcon() {}

    /** The icon at each size, for {@code stage.getIcons().setAll(...)}. */
    public static List<Image> images() {
        return SIZES.stream().map(AppIcon::image).toList();
    }

    /** The icon at one size, scaled smoothly from the large source. */
    public static Image image(int size) {
        final URL source = Objects.requireNonNull(AppIcon.class.getResource(SOURCE), SOURCE);
        return new Image(source.toExternalForm(), size, size, true, true);
    }
}
