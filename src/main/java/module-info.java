/**
 * Letterblade - a desktop viewer for Outlook {@code .msg} files, built on the druvu-lib-fx toolkit.
 *
 * <p>JavaFX modules are resolved from the JDK (a JavaFX-bundled build such as Azul Zulu FX); there are deliberately no
 * {@code org.openjfx} artifact dependencies.
 */
module com.druvu.letterblade {
    requires com.druvu.lib.fx;
    requires java.desktop; // java.awt.Desktop - open a saved attachment with the OS default app
    requires javafx.controls;
    requires javafx.web;
    requires org.simplejavamail.outlookmessageparser;
    requires org.jsoup;

    // javafx.graphics reflectively instantiates the Application subclass
    exports com.druvu.letterblade to
            javafx.graphics;
}
