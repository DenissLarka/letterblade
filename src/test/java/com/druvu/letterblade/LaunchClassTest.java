package com.druvu.letterblade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import javafx.application.Application;
import org.testng.annotations.Test;

/** Unit test for the launch shape: the JavaFX Application has no main of its own, so no bundle can start on it. */
public class LaunchClassTest {

    @Test
    public void theApplicationClassDeclaresNoMain() {
        // The Java launcher starts JavaFX before the main of an Application subclass runs; on macOS that loses
        // every file opened from Finder, the Dock or "Open With".
        assertThat(Arrays.stream(LetterbladeApp.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("main");
    }

    @Test
    public void theLaunchClassIsNotAnApplication() {
        assertThat(Application.class.isAssignableFrom(Main.class)).isFalse();
    }
}
