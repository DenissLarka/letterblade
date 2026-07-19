package com.druvu.letterblade.ui;

import com.druvu.letterblade.msg.MsgService;
import com.druvu.letterblade.msg.ParsedMessage;
import com.druvu.letterblade.render.Sanitizer;
import com.druvu.letterblade.render.Sanitizer.SafeHtml;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.status.StatusBarModel;
import com.druvu.lib.fx.util.FxThreads;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookMsgAttachment;

/**
 * Letterblade's main window content and its controller: toolbar, envelope header, blocked-content bar, body surface
 * (WebView / TextArea), and the bottom status strip. Opening a file parses it off the FX thread via {@link FxExec},
 * then renders the result; opening another file replaces the current window's content.
 *
 * <p>Not an {@code Application} subclass, so it can be built and driven headlessly. The security boundary is respected
 * here: the WebView has JavaScript disabled and only ever receives content via {@code loadContent} of
 * {@link Sanitizer}-cleaned HTML.
 */
public final class MainView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private static final double BUTTON_SIZE = 42;

    private static final String DROP_HINT = "Drop a .msg file here or use Open";
    private static final String NO_BODY = "(no message body)";

    private final FxExec exec;
    private final MsgService msgService;
    private final Sanitizer sanitizer;
    private final Notifications notifications;
    private final AppHome appHome;
    private final Consumer<String> titleUpdater;
    private final Consumer<ParsedMessage> childWindowOpener;

    private final BorderPane root = new BorderPane();

    // toolbar (icon-only; tooltips carry the labels)
    private final Button openButton = new Button();
    private final Button selectAllButton = new Button();
    private final Button copyButton = new Button();
    private final ToggleButton plainToggle = new ToggleButton();

    // envelope header
    private final Label subjectLabel = new Label();
    private final Label fromValue = new Label();
    private final Label toValue = new Label();
    private final Label ccValue = new Label();
    private final Label dateValue = new Label();
    private final HBox toRow = fieldRow("To", toValue);
    private final HBox ccRow = fieldRow("Cc", ccValue);
    private final HBox dateRow = fieldRow("Date", dateValue);

    // attachment chips (files + embedded messages), hidden when the message has none
    private final FlowPane attachmentsRow = buildAttachmentsRow();

    // blocked-content bar
    private final Label blockedLabel = new Label();
    private final Button loadRemoteButton = new Button("Load remote images");
    private final HBox blockedBar = buildBlockedBar();

    // body surface
    private final Label placeholder = new Label(DROP_HINT);
    private final WebView webView = new WebView();
    private final WebEngine webEngine = webView.getEngine();
    private final TextArea textArea = new TextArea();
    private final StackPane bodyStack = new StackPane(webView, textArea, placeholder);

    private ParsedMessage currentMessage;
    private Sanitizer.Mode sanitizeMode = Sanitizer.Mode.DEFAULT;

    public MainView(
            FxExec exec,
            MsgService msgService,
            Sanitizer sanitizer,
            Notifications notifications,
            StatusBarModel statusBarModel,
            AppHome appHome,
            Consumer<String> titleUpdater,
            Consumer<ParsedMessage> childWindowOpener) {
        this.exec = Objects.requireNonNull(exec, "exec");
        this.msgService = Objects.requireNonNull(msgService, "msgService");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.appHome = Objects.requireNonNull(appHome, "appHome");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
        this.childWindowOpener = Objects.requireNonNull(childWindowOpener, "childWindowOpener");
        Objects.requireNonNull(statusBarModel, "statusBarModel");

        // Security invariant: JavaScript off, before any content is ever loaded.
        webEngine.setJavaScriptEnabled(false);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        webView.setVisible(false);
        textArea.setVisible(false);

        wireToolbar();
        buildHeaderState();
        assignNodeIds();

        root.setTop(new VBox(buildToolbar(), buildHeader(), attachmentsRow, blockedBar));
        root.setCenter(bodyStack);
        root.setBottom(buildStatusStrip(statusBarModel));
    }

    /** The root node for the primary scene. */
    public Parent node() {
        return root;
    }

    /**
     * Parses {@code file} off the FX thread and shows it; a failure raises a friendly error toast and leaves the app
     * running. Call on the FX thread.
     */
    public void openFile(File file) {
        FxThreads.requireFx();
        Objects.requireNonNull(file, "file");
        exec.supply(file.getName(), () -> msgService.parse(file))
                .whenCompleteAsync(
                        (parsed, error) -> {
                            if (error == null) {
                                showMessage(parsed);
                            } else {
                                notifications.error(
                                        "Could not open " + file.getName() + " - not a readable Outlook .msg file");
                            }
                        },
                        FxThreads.fxExecutor());
    }

    /** Installs scene-level drag-and-drop of a single {@code .msg} file. */
    public void installDragAndDrop(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        scene.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            final Dragboard board = event.getDragboard();
            boolean handled = false;
            if (board.hasFiles() && !board.getFiles().isEmpty()) {
                final File file = board.getFiles().get(0);
                if (isMsgFile(file)) {
                    openFile(file);
                    handled = true;
                } else {
                    notifications.warning("Not a .msg file: " + file.getName());
                }
            }
            event.setDropCompleted(handled);
            event.consume();
        });
    }

    private void wireToolbar() {
        openButton.setGraphic(Icons.open());
        openButton.setTooltip(new Tooltip("Open"));
        copyButton.setGraphic(Icons.copy());
        copyButton.setTooltip(new Tooltip("Copy"));
        selectAllButton.setGraphic(Icons.selectAll());
        selectAllButton.setTooltip(new Tooltip("Select all"));
        plainToggle.setGraphic(Icons.plainText());
        plainToggle.setTooltip(new Tooltip("Plain text"));
        loadRemoteButton.setGraphic(Icons.image());

        square(openButton);
        square(selectAllButton);
        square(copyButton);
        square(plainToggle);

        openButton.setOnAction(event -> chooseAndOpen());
        copyButton.setOnAction(event -> copyPlainText());
        selectAllButton.setOnAction(event -> {
            textArea.selectAll();
            textArea.requestFocus();
        });
        plainToggle.setOnAction(event -> render());
        loadRemoteButton.setOnAction(event -> loadRemoteImages());

        copyButton.setDisable(true);
        selectAllButton.setDisable(true);
        plainToggle.setDisable(true);
    }

    private void chooseAndOpen() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Outlook message");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Outlook messages (*.msg)", "*.msg"));
        final File file = chooser.showOpenDialog(window());
        if (file != null) {
            openFile(file);
        }
    }

    /**
     * Shows an already-parsed message - the entry point shared by the open/CLI flow and by embedded-message windows.
     * Resets to the default (no-remote) sanitize mode and the rendered view. Call on the FX thread.
     */
    public void showMessage(ParsedMessage message) {
        currentMessage = message;
        sanitizeMode = Sanitizer.Mode.DEFAULT;
        updateHeader(message);
        updateAttachments(message);
        titleUpdater.accept(
                isBlank(message.subject()) ? "Letterblade" : message.subject().strip() + " - Letterblade");
        copyButton.setDisable(false);
        plainToggle.setDisable(false);
        plainToggle.setSelected(false); // default to rendered view
        render();
    }

    private void render() {
        if (currentMessage == null) {
            return;
        }
        // A message with no body at all: show a placeholder instead of an empty WebView/TextArea (no crash).
        if (isBlank(currentMessage.bodyHtml()) && currentMessage.plainText().isBlank()) {
            updateBlockedBar(0); // an empty body has no remote images; clear any bar left by a previous message
            placeholder.setText(NO_BODY);
            showBody(placeholder);
            selectAllButton.setDisable(true);
            return;
        }
        if (plainToggle.isSelected()) {
            textArea.setText(currentMessage.plainText());
            showBody(textArea);
            selectAllButton.setDisable(false);
        } else {
            final SafeHtml safe = sanitizer.sanitize(currentMessage, sanitizeMode);
            updateBlockedBar(safe.blockedRemoteCount());
            webEngine.loadContent(safe.html());
            showBody(webView);
            selectAllButton.setDisable(true);
        }
    }

    private void loadRemoteImages() {
        sanitizeMode = Sanitizer.Mode.ALLOW_REMOTE;
        plainToggle.setSelected(false); // remote images only matter in the rendered view
        render();
    }

    private void copyPlainText() {
        if (currentMessage == null) {
            return;
        }
        final ClipboardContent content = new ClipboardContent();
        content.putString(currentMessage.plainText());
        Clipboard.getSystemClipboard().setContent(content);
        notifications.success("Copied");
    }

    private void updateHeader(ParsedMessage message) {
        final boolean hasSubject = !isBlank(message.subject());
        final String subject = hasSubject ? message.subject().strip() : "(no subject)";
        subjectLabel.setText(subject);
        // Long subjects ellipsize on one line (see buildHeader); the tooltip carries the full text.
        subjectLabel.setTooltip(hasSubject ? new Tooltip(subject) : null);
        fromValue.setText(formatFrom(message.fromName(), message.fromEmail()));
        setRow(toRow, toValue, message.displayTo());
        setRow(ccRow, ccValue, message.displayCc());
        final boolean hasDate = message.date() != null;
        dateRow.setVisible(hasDate);
        dateRow.setManaged(hasDate);
        if (hasDate) {
            dateValue.setText(DATE_FORMAT.format(message.date()));
        }
    }

    /** Rebuilds the chip row: one chip per real attachment, one per embedded message; hidden when there are none. */
    private void updateAttachments(ParsedMessage message) {
        attachmentsRow.getChildren().clear();
        for (OutlookFileAttachment attachment : message.attachments()) {
            attachmentsRow.getChildren().add(fileChip(attachment));
        }
        for (OutlookMsgAttachment embedded : message.embeddedMessages()) {
            attachmentsRow.getChildren().add(embeddedChip(embedded));
        }
        final boolean any = !attachmentsRow.getChildren().isEmpty();
        attachmentsRow.setVisible(any);
        attachmentsRow.setManaged(any);
    }

    private Button fileChip(OutlookFileAttachment attachment) {
        final String name = Attachments.displayName(
                attachment.getLongFilename(), attachment.getFilename(), attachment.getExtension());
        final Button chip =
                new Button(name + " (" + Attachments.humanSize(attachment.getSize()) + ")", Icons.paperclip());
        chip.setTooltip(new Tooltip("Save or open " + name));
        chip.setOnAction(event -> showAttachmentDialog(attachment, name));
        return chip;
    }

    private Button embeddedChip(OutlookMsgAttachment embedded) {
        final String subject = embedded.getOutlookMessage().getSubject();
        final Button chip = new Button(isBlank(subject) ? "(embedded message)" : subject.strip(), Icons.envelope());
        chip.setTooltip(new Tooltip("Open embedded message in a new window"));
        chip.setOnAction(event -> openEmbedded(embedded));
        return chip;
    }

    private void openEmbedded(OutlookMsgAttachment embedded) {
        try {
            // The nested message is already in memory (parsed with the enclosing file), so mapping is a cheap,
            // side-effect-free transform - safe to run on the FX thread.
            childWindowOpener.accept(msgService.parseEmbedded(embedded));
        } catch (RuntimeException ex) {
            notifications.error("Could not open the embedded message");
        }
    }

    /** Modal detail dialog for a file attachment: name, size, MIME, with Cancel / Save… / Open. */
    private void showAttachmentDialog(OutlookFileAttachment attachment, String name) {
        final Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Attachment");
        dialog.setHeaderText(null);
        final Window owner = window();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        final ButtonType saveType = new ButtonType("Save…", ButtonBar.ButtonData.APPLY);
        final ButtonType openType = new ButtonType("Open", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType, openType);

        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        grid.addRow(0, boldLabel("File"), new Label(name));
        grid.addRow(1, boldLabel("Size"), new Label(Attachments.humanSize(attachment.getSize())));
        if (!isBlank(attachment.getMimeTag())) {
            grid.addRow(2, boldLabel("Type"), new Label(attachment.getMimeTag().strip()));
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button);

        dialog.showAndWait().ifPresent(button -> {
            if (button == saveType) {
                saveAttachment(attachment, name);
            } else if (button == openType) {
                openAttachment(attachment, name);
            }
        });
    }

    private void saveAttachment(OutlookFileAttachment attachment, String name) {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Save attachment");
        chooser.setInitialFileName(Attachments.safeFileName(name));
        final File target = chooser.showSaveDialog(window());
        if (target == null) {
            return;
        }
        final byte[] data = attachment.getData();
        exec.run("Saving " + name, () -> Files.write(target.toPath(), data))
                .whenCompleteAsync(
                        (ignored, error) -> {
                            if (error == null) {
                                notifications.success("Saved " + target.getName());
                            } else {
                                notifications.error("Could not save " + name);
                            }
                        },
                        FxThreads.fxExecutor());
    }

    private void openAttachment(OutlookFileAttachment attachment, String name) {
        if (!Desktop.isDesktopSupported()) {
            notifications.warning("Opening attachments is not supported on this system");
            return;
        }
        final byte[] data = attachment.getData();
        final String safe = Attachments.safeFileName(name);
        // Write to the app home, then hand off to the OS default app - never auto-opened, only on this explicit action.
        exec.run("Opening " + name, () -> {
                    final Path file = appHome.dir("attachments").resolve(safe);
                    Files.write(file, data);
                    Desktop.getDesktop().open(file.toFile());
                })
                .whenCompleteAsync(
                        (ignored, error) -> {
                            if (error != null) {
                                notifications.error("Could not open " + name);
                            }
                        },
                        FxThreads.fxExecutor());
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void updateBlockedBar(int count) {
        final boolean show = count > 0;
        blockedBar.setVisible(show);
        blockedBar.setManaged(show);
        if (show) {
            blockedLabel.setText(count + (count == 1 ? " remote image blocked" : " remote images blocked"));
        }
    }

    private void showBody(Node visible) {
        placeholder.setVisible(visible == placeholder);
        webView.setVisible(visible == webView);
        textArea.setVisible(visible == textArea);
    }

    private ToolBar buildToolbar() {
        return new ToolBar(openButton, new Separator(), selectAllButton, copyButton, new Separator(), plainToggle);
    }

    private VBox buildHeader() {
        subjectLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        // One line with an ellipsis when too long (default Label overflow); the full subject is in the tooltip.
        subjectLabel.setWrapText(false);
        final VBox header = new VBox(4, subjectLabel, fieldRow("From", fromValue), toRow, ccRow, dateRow);
        header.setPadding(new Insets(8, 12, 8, 12));
        return header;
    }

    /** Stable node ids so headless UI tests can look nodes up; no effect on behaviour. */
    private void assignNodeIds() {
        subjectLabel.setId("subject");
        attachmentsRow.setId("attachmentsRow");
        blockedBar.setId("blockedBar");
        placeholder.setId("bodyPlaceholder");
        toRow.setId("toRow");
        ccRow.setId("ccRow");
        dateRow.setId("dateRow");
    }

    private FlowPane buildAttachmentsRow() {
        final FlowPane row = new FlowPane(8, 8);
        row.setPadding(new Insets(0, 12, 8, 12));
        row.setVisible(false);
        row.setManaged(false);
        return row;
    }

    private HBox buildBlockedBar() {
        final HBox bar = new HBox(10, blockedLabel, loadRemoteButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.setStyle("-fx-background-color: #fff3cd;");
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    private HBox buildStatusStrip(StatusBarModel statusBarModel) {
        final Label tasksLabel = new Label();
        tasksLabel.textProperty().bind(statusBarModel.runningTasksProperty().asString("tasks: %d"));
        final Label messageLabel = new Label();
        messageLabel.textProperty().bind(statusBarModel.messageProperty());

        final HBox strip = new HBox(16, tasksLabel, new Separator(Orientation.VERTICAL), messageLabel);
        strip.setPadding(new Insets(4, 8, 4, 8));
        strip.setAlignment(Pos.CENTER_LEFT);
        return strip;
    }

    private void buildHeaderState() {
        subjectLabel.setText("");
        toRow.setVisible(false);
        toRow.setManaged(false);
        ccRow.setVisible(false);
        ccRow.setManaged(false);
        dateRow.setVisible(false);
        dateRow.setManaged(false);
    }

    private static void setRow(HBox row, Label value, String text) {
        final boolean show = !isBlank(text);
        row.setVisible(show);
        row.setManaged(show);
        if (show) {
            value.setText(text.strip());
        }
    }

    private static Label boldLabel(String text) {
        final Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static HBox fieldRow(String name, Label value) {
        final Label key = new Label(name);
        key.setMinWidth(48);
        key.setStyle("-fx-font-weight: bold;");
        value.setWrapText(true);
        HBox.setHgrow(value, Priority.ALWAYS);
        final HBox row = new HBox(8, key, value);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static String formatFrom(String name, String email) {
        final boolean hasName = !isBlank(name);
        final boolean hasEmail = !isBlank(email);
        if (hasName && hasEmail) {
            return name.strip() + " <" + email.strip() + ">";
        }
        if (hasEmail) {
            return email.strip();
        }
        if (hasName) {
            return name.strip();
        }
        return "(unknown sender)";
    }

    private static boolean isMsgFile(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".msg");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Fixes a toolbar control to a square {@link #BUTTON_SIZE} so the icon-only buttons are uniform. */
    private static void square(Control control) {
        control.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        control.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
    }
}
