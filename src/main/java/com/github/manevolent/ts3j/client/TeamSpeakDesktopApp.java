package com.github.manevolent.ts3j.client;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact, keyboard-friendly JavaFX client shell around the ts3j gateway. */
public final class TeamSpeakDesktopApp extends Application {
    private enum ControlIcon {
        AWAY,
        MICROPHONE,
        SPEAKERS
    }

    private static final int MIN_WIDTH = 920;
    private static final int MIN_HEIGHT = 600;
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock clock = Clock.systemUTC();
    private BorderPane root;
    private VBox channelList;
    private VBox mainContent;
    private ScrollPane mainScroll;
    private BorderPane mainPane;
    private VBox activeTextRoot;
    private TextField activeChatComposer;
    private boolean restoreChatComposerFocus;
    private boolean keepChatComposerFocused;
    private Label statusLabel;
    private Label statusDetailLabel;
    private Label bottomTimerLabel;
    private Label selectedTimerLabel;
    private Label errorLabel;
    private Button connectButton;
    private Button disconnectButton;
    private Button awayButton;
    private Button micButton;
    private Button audioButton;
    private Stage stage;
    private DesktopTray desktopTray;
    private SingleInstanceGuard singleInstanceGuard;
    private AudioDeviceService audioDeviceService;
    private VoiceNotificationService voiceNotificationService;
    private AppPreferences appPreferences;
    private WindowsStartupManager startupManager;
    private ClientVolumeStore clientVolumeStore;
    private boolean trayInstalled;
    private boolean forceExit;
    private boolean lightTheme;
    private UiLanguage language;
    private boolean demoMode;
    private int selectedChannelId = -1;
    private boolean selectedTextView;
    private GatewaySnapshot current;
    private TeamSpeakGateway gateway;
    private VoiceSessionCoordinator coordinator;
    private Timeline timerTimeline;
    private final Map<Label, VoiceRoomSession> timerLabels = new HashMap<>();
    private final ConnectionProfileStore connectionProfileStore = new ConnectionProfileStore();
    private volatile double latestMicrophoneLevel;
    private volatile double latestPlaybackLevel;
    private ProgressBar audioCaptureMeterControl;
    private ProgressBar audioPlaybackMeterControl;
    private Label audioCaptureLevelControl;
    private Label audioPlaybackLevelControl;
    private final UpdateService updateService = new UpdateService();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts3j-client-updates");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void start(Stage stage) {
        boolean demo = getParameters().getRaw().contains("--demo");
        boolean compact = getParameters().getRaw().contains("--compact");
        boolean noTray = getParameters().getRaw().contains("--no-tray");
        this.stage = stage;
        singleInstanceGuard = new SingleInstanceGuard(defaultStatePath().getParent());
        try {
            if (!singleInstanceGuard.acquire(this::focusExistingInstance,
                    this::exitFromInstaller)) {
                Platform.exit();
                return;
            }
        } catch (IOException error) {
            Platform.exit();
            return;
        }
        appPreferences = new AppPreferences();
        startupManager = new WindowsStartupManager();
        clientVolumeStore = new ClientVolumeStore();
        language = appPreferences.language();
        lightTheme = appPreferences.isLightTheme();
        desktopTray = new DesktopTray(language);
        audioDeviceService = new AudioDeviceService(appPreferences.captureDevice(),
                appPreferences.playbackDevice());
        audioDeviceService.setCaptureLevelListener(this::handleMicrophoneLevel);
        audioDeviceService.setOutputLevelListener(this::handlePlaybackLevel);
        audioDeviceService.start();
        voiceNotificationService = new VoiceNotificationService(language,
                appPreferences.voiceNotifications(), appPreferences.voiceNotificationVolume());
        demoMode = demo;
        VoiceSessionRepository repository;
        if (demo) {
            repository = new InMemoryVoiceSessionRepository();
        } else {
            repository = new FileVoiceSessionRepository(defaultStatePath());
        }
        coordinator = new VoiceSessionCoordinator(repository, clock);
        gateway = new TeamSpeakGateway(coordinator, clock);
        gateway.addActivityListener(activity -> voiceNotificationService.enqueue(activity));
        gateway.addListener(snapshot -> {
            if (Platform.isFxApplicationThread()) {
                render(snapshot);
            } else {
                Platform.runLater(() -> render(snapshot));
            }
        });

        root = new BorderPane();
        root.getStyleClass().add("app-root");
        if (lightTheme) root.getStyleClass().add("theme-light");
        root.setCenter(buildWorkspace());
        root.setBottom(buildConnectionBar());

        if (demo) {
            current = DemoData.snapshot();
            selectedChannelId = current.getCurrentChannelId();
        } else {
            current = gateway.snapshot();
        }

        Scene scene = new Scene(root, compact ? 920 : 1180, compact ? 600 : 760);
        scene.getStylesheets().add(getClass().getResource("/com/github/manevolent/ts3j/client/app.css").toExternalForm());
        stage.setTitle("ts3j client");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        setStageIcon(stage);
        trayInstalled = !noTray && desktopTray.install(stage, this::restoreFromTray, this::exitApplication);
        if (trayInstalled) {
            // Hiding the last JavaFX window must keep the application alive so the
            // tray icon can restore it. JavaFX exits automatically otherwise.
            Platform.setImplicitExit(false);
        }
        stage.setOnCloseRequest(event -> {
            if (!forceExit && appPreferences.closesToTray() && trayInstalled) {
                event.consume();
                desktopTray.hide();
            } else if (!forceExit && trayInstalled) {
                // The tray keeps JavaFX alive while hidden; restore the normal
                // lifecycle when the user explicitly chooses to close fully.
                forceExit = true;
                desktopTray.close();
                Platform.setImplicitExit(true);
            }
        });
        if (getParameters().getRaw().contains("--tray") && trayInstalled) {
            stage.hide();
        } else {
            stage.show();
        }

        render(current);
        startTimer();
    }

    /**
     * Uses the same bundled artwork as the tray and installer so the native
     * window chrome does not fall back to JavaFX's generic application icon.
     */
    private void setStageIcon(Stage target) {
        URL iconUrl = getClass().getResource("/com/github/manevolent/ts3j/client/ts3j-client.png");
        if (iconUrl != null) {
            target.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
    }

    private Node buildWorkspace() {
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.06, 0.28);
        split.getStyleClass().add("workspace-split");
        split.getItems().addAll(buildServerRail(), buildChannelPane(), buildMainPane());
        return split;
    }

    private Node buildServerRail() {
        VBox rail = new VBox(12);
        rail.setPadding(new Insets(12, 8, 12, 8));
        rail.setAlignment(Pos.TOP_CENTER);
        rail.getStyleClass().add("server-rail");

        Button server = new Button("T");
        server.setMinSize(44, 44);
        server.setMaxSize(44, 44);
        server.setAccessibleText(t("server.description"));
        server.setTooltip(new Tooltip(t("server.description")));
        server.getStyleClass().addAll("server-pill", "selected");
        rail.getChildren().add(server);

        Separator separator = new Separator();
        separator.setMaxWidth(32);
        rail.getChildren().add(separator);

        Button add = iconButton("+", t("add.connection"), () -> showConnectionDialog());
        add.getStyleClass().add("server-pill");
        rail.getChildren().add(add);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        rail.getChildren().add(spacer);

        Button theme = iconButton("☼", t("theme.toggle"), () -> toggleTheme());
        rail.getChildren().add(theme);
        return rail;
    }

    private Node buildChannelPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(16, 12, 12, 12));
        pane.getStyleClass().add("channel-pane");

        HBox heading = new HBox(8);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(t("server"));
        title.getStyleClass().add("pane-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button settings = new Button("⚙  " + t("preferences"));
        settings.setMinHeight(34);
        settings.setAccessibleText(t("preferences"));
        settings.setTooltip(new Tooltip(t("preferences")));
        settings.setOnAction(event -> showSettingsDialog());
        settings.getStyleClass().add("settings-button");
        heading.getChildren().addAll(title, spacer, settings);
        pane.getChildren().add(heading);

        Label subtitle = new Label(t("channels"));
        subtitle.getStyleClass().add("section-label");
        pane.getChildren().add(subtitle);

        channelList = new VBox(4);
        ScrollPane scroll = new ScrollPane(channelList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("channel-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        pane.getChildren().add(scroll);
        return pane;
    }

    private Node buildMainPane() {
        mainContent = new VBox(18);
        mainContent.setPadding(new Insets(22, 28, 24, 28));
        mainContent.setId("main-content");
        mainContent.getStyleClass().add("main-content");
        mainScroll = new ScrollPane(mainContent);
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScroll.getStyleClass().add("main-scroll");
        mainPane = new BorderPane();
        mainPane.getStyleClass().add("main-pane");
        mainPane.setCenter(mainScroll);
        return mainPane;
    }

    private Node buildConnectionBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("connection-bar");

        Label indicator = new Label("●");
        indicator.setId("status-indicator");
        statusLabel = new Label(t("status.disconnected"));
        statusLabel.getStyleClass().add("status-label");
        statusDetailLabel = new Label(t("status.disconnected.detail"));
        statusDetailLabel.getStyleClass().add("muted-text");
        VBox status = new VBox(2, statusLabel, statusDetailLabel);
        HBox.setHgrow(status, Priority.ALWAYS);

        Label timerCaption = new Label(t("voice.time"));
        timerCaption.getStyleClass().add("muted-text");
        bottomTimerLabel = new Label("—");
        bottomTimerLabel.getStyleClass().add("bottom-timer");
        VBox timer = new VBox(1, timerCaption, bottomTimerLabel);
        timer.setAlignment(Pos.CENTER_RIGHT);

        connectButton = new Button(t("connect"));
        connectButton.setGraphic(new Label("↗"));
        connectButton.setTooltip(new Tooltip(t("connect.server")));
        connectButton.setAccessibleText(t("connect.server"));
        connectButton.setOnAction(event -> showConnectionDialog());
        connectButton.getStyleClass().add("primary-button");

        disconnectButton = new Button(t("leave"));
        disconnectButton.setGraphic(new Label("×"));
        disconnectButton.setTooltip(new Tooltip(t("disconnect")));
        disconnectButton.setAccessibleText(t("disconnect"));
        disconnectButton.setOnAction(event -> gateway.disconnect());
        disconnectButton.getStyleClass().add("quiet-button");

        awayButton = iconButton(ControlIcon.AWAY, t("away.available"), this::toggleAwayStatus);
        micButton = iconButton(ControlIcon.MICROPHONE, t("mic.available"), this::toggleMicrophoneMute);
        audioButton = iconButton(ControlIcon.SPEAKERS, t("audio.available"), this::toggleAudioMute);
        bar.setFocusTraversable(true);
        installMouseFocusReset(awayButton, bar);
        installMouseFocusReset(micButton, bar);
        installMouseFocusReset(audioButton, bar);

        bar.getChildren().addAll(indicator, status, timer, awayButton, micButton, audioButton,
                disconnectButton, connectButton);
        return bar;
    }

    private void installMouseFocusReset(Button button, Node focusSink) {
        button.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.isStillSincePress()) {
                Platform.runLater(focusSink::requestFocus);
            }
        });
    }

    private void render(GatewaySnapshot snapshot) {
        if (snapshot == null || root == null) return;
        current = snapshot;
        if (snapshot.getStatus() == ConnectionStatus.CONNECTING
                || snapshot.getStatus() == ConnectionStatus.DISCONNECTED
                || snapshot.getStatus() == ConnectionStatus.ERROR) {
            selectedTextView = false;
        }
        if (selectedChannelId < 0 || !snapshot.getChannels().containsKey(selectedChannelId)) {
            selectedChannelId = snapshot.getCurrentChannelId();
            if (selectedChannelId < 0 && !snapshot.getChannels().isEmpty()) {
                selectedChannelId = snapshot.getChannels().keySet().iterator().next();
            }
        }
        renderChannels(snapshot);
        renderMain(snapshot);
        renderConnection(snapshot);
    }

    private void renderChannels(GatewaySnapshot snapshot) {
        timerLabels.clear();
        channelList.getChildren().clear();
        List<ChannelView> channels = new ArrayList<>(snapshot.getChannels().values());
        Collections.sort(channels, Comparator.comparingInt(ChannelView::getOrder)
                .thenComparing(ChannelView::getName));

        Label textHeader = new Label(t("text.header"));
        textHeader.getStyleClass().add("section-label");
        channelList.getChildren().add(textHeader);
        boolean hasText = false;
        for (ChannelView channel : channels) {
            if (channel.isTextCapable()) {
                channelList.getChildren().add(channelItem(channel, true));
                hasText = true;
            }
        }
        if (!hasText) channelList.getChildren().add(emptyLine(t("no.text.channels")));

        Label voiceHeader = new Label(t("voice.header"));
        voiceHeader.getStyleClass().add("section-label");
        VBox.setMargin(voiceHeader, new Insets(16, 0, 0, 0));
        channelList.getChildren().add(voiceHeader);
        boolean hasVoice = false;
        for (ChannelView channel : channels) {
            if (channel.isVoiceCapable()) {
                channelList.getChildren().add(channelItem(channel, false));
                hasVoice = true;
            }
        }
        if (!hasVoice) channelList.getChildren().add(emptyLine(t("no.voice.channels")));
    }

    private Node channelRow(ChannelView channel, boolean textMode) {
        VoiceRoomSession session = textMode ? null : sessionFor(current, channel.getId());
        Button row = new Button();
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(38);
        String kind = textMode ? t("text.channel") : t("voice.channel");
        row.setAccessibleText(kind + " " + channel.getName());
        row.setTooltip(new Tooltip(t("channel.tooltip", channel.getName(), kind.toLowerCase(language.getLocale()))));
        row.getStyleClass().add("channel-row");
        row.getStyleClass().add(textMode ? "text-channel-row" : "voice-channel-row");
        if (channel.getId() == selectedChannelId && textMode == selectedTextView) {
            row.getStyleClass().add("selected");
        }

        Label icon = new Label(textMode ? "#" : "◉");
        icon.getStyleClass().add("channel-icon");
        Label name = new Label(channel.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox content = new HBox(8, icon, name);
        if (!textMode) {
            Label timer = new Label();
            timer.getStyleClass().add("channel-timer");
            applyTimerPresentation(timer, session, clock.instant());
            if (session != null) timerLabels.put(timer, session);
            content.getChildren().add(timer);
        }
        content.setAlignment(Pos.CENTER_LEFT);
        row.setGraphic(content);
        row.setOnAction(event -> {
            // A composer-focus request belongs only to the channel that
            // initiated the send.  Do not carry it into an intentional
            // navigation action and unexpectedly steal focus back.
            keepChatComposerFocused = false;
            selectedChannelId = channel.getId();
            selectedTextView = textMode;
            render(current);
        });
        return row;
    }

    private Node channelItem(ChannelView channel, boolean textMode) {
        VBox item = new VBox(2);
        item.getChildren().add(channelRow(channel, textMode));
        if (!textMode && channel.isVoiceCapable()) {
            for (ClientView user : usersInChannel(current, channel.getId())) {
                Label member = new Label("·  " + user.getNickname());
                member.getStyleClass().add("channel-user");
                member.setTooltip(new Tooltip(user.getNickname()));
                item.getChildren().add(member);
            }
        }
        return item;
    }

    private void renderTextChannel(GatewaySnapshot snapshot, ChannelView channel) {
        VBox textRoot = new VBox(18);
        textRoot.setPadding(new Insets(22, 28, 24, 28));
        textRoot.setFillWidth(true);
        textRoot.setId("text-channel-root");
        textRoot.getStyleClass().add("main-content");

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("chat-channel-header");
        Label channelIcon = new Label("#");
        channelIcon.getStyleClass().add("chat-channel-mark");
        VBox titleBox = new VBox(3);
        Label eyebrow = new Label(t("text.channel").toUpperCase(language.getLocale()));
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label(channel.getName());
        title.getStyleClass().add("main-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        heading.getChildren().addAll(channelIcon, titleBox, spacer);
        textRoot.getChildren().add(heading);

        Label rule = new Label(t("text.rule"));
        rule.getStyleClass().add("muted-text");
        textRoot.getChildren().add(rule);
        textRoot.getChildren().add(new Separator());

        VBox messagesBox = new VBox(8);
        messagesBox.getStyleClass().add("chat-messages");
        messagesBox.setMaxWidth(Double.MAX_VALUE);
        messagesBox.setFillWidth(true);
        List<ChannelTextMessage> messages = snapshot.getChannelMessages().get(channel.getId());
        if (messages == null || messages.isEmpty()) {
            messagesBox.getChildren().add(emptyLine(t("chat.empty")));
        } else {
            int historyBoundary = snapshot.getChannelHistoryBoundaries()
                    .containsKey(channel.getId())
                    ? snapshot.getChannelHistoryBoundaries().get(channel.getId()) : -1;
            LocalDate previousDay = null;
            ChannelTextMessage previous = null;
            for (int index = 0; index < messages.size(); index++) {
                if (index == historyBoundary) {
                    messagesBox.getChildren().add(chatHistoryBoundary());
                }
                ChannelTextMessage message = messages.get(index);
                LocalDate day = chatDate(message.getReceivedAt());
                if (previousDay == null || !previousDay.equals(day)) {
                    messagesBox.getChildren().add(chatDayDivider(day));
                }
                boolean grouped = previous != null && isGrouped(previous, message);
                messagesBox.getChildren().add(chatMessage(message, grouped));
                previous = message;
                previousDay = day;
            }
            if (historyBoundary >= messages.size()) {
                messagesBox.getChildren().add(chatHistoryBoundary());
            }
        }
        ScrollPane messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messagesScroll.setMinHeight(0);
        messagesScroll.setMaxHeight(Double.MAX_VALUE);
        messagesScroll.getStyleClass().add("chat-scroll");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);
        textRoot.getChildren().add(messagesScroll);

        TextField composer = new TextField();
        composer.setPromptText(t("text.compose"));
        composer.setAccessibleHelp(t("text.compose.help"));
        HBox.setHgrow(composer, Priority.ALWAYS);
        Button send = new Button("↵");
        send.setTooltip(new Tooltip(t("text.send.tooltip")));
        send.setAccessibleText(t("text.send"));
        send.getStyleClass().add("chat-send-button");
        Runnable sendMessage = new Runnable() {
            @Override
            public void run() {
                String text = composer.getText().trim();
                if (text.isEmpty()) return;
                keepChatComposerFocused = true;
                try {
                    gateway.sendChannelMessage(channel.getId(), text);
                    composer.clear();
                } catch (RuntimeException error) {
                    addError(error.getMessage());
                } finally {
                    // Button activation can leave the button focused and an
                    // accepted send can trigger an asynchronous snapshot
                    // rebuild. Refocus the current field now; keep the
                    // request set until the replacement field confirms focus
                    // so later snapshots restore the newly-created field too.
                    Platform.runLater(() -> {
                        refocusComposer(composer);
                    });
                }
            }
        };
        composer.setOnAction(event -> sendMessage.run());
        send.setOnAction(event -> sendMessage.run());
        HBox composerRow = new HBox(8, composer, send);
        composerRow.getStyleClass().add("chat-composer");
        composerRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(composerRow, new Insets(0, 0, 0, 0));
        textRoot.getChildren().add(composerRow);

        boolean shouldRestoreFocus = restoreChatComposerFocus;
        restoreChatComposerFocus = false;
        activeChatComposer = composer;
        activeTextRoot = textRoot;
        mainPane.setCenter(textRoot);
        Platform.runLater(() -> {
            messagesScroll.setVvalue(1.0);
            if (shouldRestoreFocus) {
                refocusComposer(composer);
                // A single send can produce several snapshots (local append,
                // server echo, and history persistence). Keep the request
                // alive until the field created by the last snapshot actually
                // owns focus; otherwise a second snapshot can rebuild the
                // view between requestFocus() and the next FX pulse.
                Platform.runLater(() -> {
                    if (activeChatComposer == composer && composer.isFocused()) {
                        keepChatComposerFocused = false;
                    }
                });
            } else if (activeChatComposer == composer) {
                keepChatComposerFocused = false;
            }
        });
    }

    private static void refocusComposer(TextField composer) {
        if (composer == null || composer.getScene() == null) return;
        composer.requestFocus();
        composer.positionCaret(composer.getText().length());
    }

    private Node chatMessage(ChannelTextMessage message, boolean grouped) {
        HBox row = new HBox(10);
        row.getStyleClass().add("chat-message");
        if (grouped) row.getStyleClass().add("chat-message-grouped");

        Node avatar;
        if (grouped) {
            Region spacer = new Region();
            spacer.setMinSize(32, 32);
            spacer.setMaxSize(32, 32);
            spacer.getStyleClass().add("chat-avatar-spacer");
            avatar = spacer;
        } else {
            Label avatarLabel = new Label(initials(message.getSender()));
            avatarLabel.getStyleClass().add("chat-avatar");
            avatarLabel.setAccessibleText(t("chat.avatar", message.getSender()));
            avatar = avatarLabel;
        }

        VBox content = new VBox(3);
        content.getStyleClass().add("chat-message-content");
        HBox meta = new HBox(8);
        meta.setAlignment(Pos.BASELINE_LEFT);
        Label sender = new Label(message.getSender());
        sender.getStyleClass().add("chat-sender");
        Label time = new Label(formatChatTime(message.getReceivedAt()));
        time.getStyleClass().add("chat-time");
        meta.getChildren().addAll(sender, time);
        Label body = new Label(message.getMessage());
        body.setWrapText(true);
        body.setMaxWidth(Double.MAX_VALUE);
        body.getStyleClass().add("chat-body");
        content.getChildren().addAll(meta, body);
        HBox.setHgrow(content, Priority.ALWAYS);
        row.getChildren().addAll(avatar, content);
        row.setAccessibleText(message.getSender() + " " + formatChatTime(message.getReceivedAt())
                + ": " + message.getMessage());
        return row;
    }

    private Node chatDayDivider(LocalDate date) {
        HBox divider = new HBox(10);
        divider.setAlignment(Pos.CENTER);
        divider.getStyleClass().add("chat-day-divider");
        Region left = new Region();
        Region right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        Label label = new Label(capitalize(dateTimeFormatter("EEEE").format(date), language.getLocale()));
        label.getStyleClass().add("chat-day-label");
        divider.getChildren().addAll(left, label, right);
        return divider;
    }

    private Node chatHistoryBoundary() {
        HBox divider = new HBox(10);
        divider.setAlignment(Pos.CENTER);
        divider.getStyleClass().add("chat-history-boundary");
        Region left = new Region();
        Region right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        Label label = new Label(t("chat.history.end"));
        label.getStyleClass().add("chat-history-label");
        divider.getChildren().addAll(left, label, right);
        return divider;
    }

    private boolean isGrouped(ChannelTextMessage previous, ChannelTextMessage current) {
        if (!previous.getSender().equals(current.getSender())) return false;
        if (!chatDate(previous.getReceivedAt()).equals(chatDate(current.getReceivedAt()))) return false;
        if (current.getReceivedAt().isBefore(previous.getReceivedAt())) return false;
        return java.time.Duration.between(previous.getReceivedAt(), current.getReceivedAt())
                .compareTo(java.time.Duration.ofMinutes(5)) <= 0;
    }

    private LocalDate chatDate(Instant timestamp) {
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).toLocalDate();
    }

    private String formatChatTime(Instant timestamp) {
        return CHAT_TIME_FORMAT.format(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()));
    }

    private static String capitalize(String value, Locale locale) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(locale) + value.substring(1);
    }

    private DateTimeFormatter dateTimeFormatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, language.getLocale());
    }

    private String t(String key, Object... arguments) {
        return UiText.text(language, key, arguments);
    }

    private static String formatVoiceVolume(int value) {
        return String.format(Locale.ROOT, "%d%%", Math.max(0, Math.min(100, value)));
    }

    private static void constrainSettingsHelp(Label label) {
        label.setWrapText(true);
        label.setMaxWidth(420);
    }

    private void renderMain(GatewaySnapshot snapshot) {
        restoreChatComposerFocus = selectedTextView && (keepChatComposerFocused
                || activeChatComposer != null && activeChatComposer.isFocused());
        // Do not consume keepChatComposerFocused here. The send acknowledgement
        // is published from an I/O thread and can be followed by a server echo;
        // renderTextChannel clears it only after the replacement field has
        // actually acquired focus. Navigating away cancels it explicitly.
        if (!selectedTextView) keepChatComposerFocused = false;
        activeChatComposer = null;
        activeTextRoot = null;
        if (mainPane != null && mainScroll != null) mainPane.setCenter(mainScroll);
        mainContent.getChildren().clear();
        errorLabel = null;
        ChannelView channel = snapshot.getChannels().get(selectedChannelId);
        if (snapshot.getStatus() == ConnectionStatus.CONNECTING) {
            ProgressIndicator progress = new ProgressIndicator();
            progress.setMaxSize(32, 32);
            mainContent.getChildren().addAll(progress, new Label(t("connecting")));
            return;
        }
        if (channel == null) {
            mainContent.getChildren().add(emptyState(snapshot));
            if (!snapshot.getErrorMessage().isEmpty()) addError(snapshot.getErrorMessage());
            addPermissionsNotice(snapshot);
            return;
        }

        if (selectedTextView && channel.isTextCapable()) {
            renderTextChannel(snapshot, channel);
            if (!snapshot.getErrorMessage().isEmpty()) addError(snapshot.getErrorMessage());
            addPermissionsNotice(snapshot);
            return;
        }

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label channelIcon = new Label(channel.isVoiceCapable() ? "◉" : "#");
        channelIcon.getStyleClass().add("heading-icon");
        VBox titleBox = new VBox(3);
        Label eyebrow = new Label((channel.isVoiceCapable() ? t("voice.channel") : t("text.channel"))
                .toUpperCase(language.getLocale()));
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label(channel.getName());
        title.getStyleClass().add("main-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VoiceRoomSession selectedSession = sessionFor(snapshot, channel.getId());
        selectedTimerLabel = new Label();
        selectedTimerLabel.getStyleClass().add("hero-timer");
        applyTimerPresentation(selectedTimerLabel, selectedSession, clock.instant());
        heading.getChildren().addAll(channelIcon, titleBox, spacer, selectedTimerLabel);
        mainContent.getChildren().add(heading);

        Label rule = new Label(channel.isVoiceCapable() ? t("voice.shared") : t("text.voiceless.rule"));
        rule.getStyleClass().add("muted-text");
        mainContent.getChildren().add(rule);
        mainContent.getChildren().add(new Separator());

        if (channel.isVoiceCapable()) {
            List<ClientView> users = usersInChannel(snapshot, channel.getId());
            Label usersHeading = new Label(t("people", users.size()));
            usersHeading.getStyleClass().add("section-label");
            mainContent.getChildren().add(usersHeading);
            VBox usersBox = new VBox(4);
            for (ClientView user : users) usersBox.getChildren().add(userRow(user));
            if (users.isEmpty()) usersBox.getChildren().add(emptyLine(t("empty.channel")));
            mainContent.getChildren().add(usersBox);
            VoiceRoomSession session = sessionFor(snapshot, channel.getId());
            if (SessionTimerPresentation.isInherited(session)) {
                Label inherited = new Label(SessionTimerPresentation.inheritedNotice(language));
                inherited.getStyleClass().add("session-source-line");
                inherited.setTooltip(new Tooltip(SessionTimerPresentation.tooltip(session, language)));
                mainContent.getChildren().add(inherited);
            }
        } else {
            mainContent.getChildren().add(emptyLine(t("select.voice")));
        }
        if (!snapshot.getErrorMessage().isEmpty()) addError(snapshot.getErrorMessage());
        addPermissionsNotice(snapshot);
    }

    private Node emptyState(GatewaySnapshot snapshot) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(snapshot.getStatus() == ConnectionStatus.DISCONNECTED ? "○" : "⌁");
        icon.getStyleClass().add("empty-icon");
        Label title = new Label(snapshot.getStatus() == ConnectionStatus.DISCONNECTED
                ? t("empty.disconnected.title") : t("empty.nochannel.title"));
        title.getStyleClass().add("main-title");
        Label body = new Label(snapshot.getStatus() == ConnectionStatus.DISCONNECTED
                ? t("empty.disconnected.body") : t("empty.nochannel.body"));
        body.getStyleClass().add("muted-text");
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private Node userRow(ClientView user) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.getStyleClass().add("user-row");
        row.setFocusTraversable(true);
        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.setAccessibleText(t("user.accessible", user.getNickname()));
        row.setOnContextMenuRequested(event -> {
            showUserContextMenu(user, row, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        row.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.CONTEXT_MENU
                    || (event.getCode() == KeyCode.F10 && event.isShiftDown())) {
                Bounds bounds = row.localToScreen(row.getBoundsInLocal());
                if (bounds != null) showUserContextMenu(user, row, bounds.getMinX(), bounds.getMaxY());
                event.consume();
            }
        });
        Label avatar = new Label(initials(user.getNickname()));
        avatar.getStyleClass().add("avatar");
        Label name = new Label(user.getNickname());
        name.getStyleClass().add("user-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        row.getChildren().addAll(avatar, name);
        if (user.isInputMuted()) {
            Label mute = new Label(t("mic.off"));
            mute.getStyleClass().add("mute-badge");
            mute.setTooltip(new Tooltip(t("mic.tooltip")));
            row.getChildren().add(mute);
        }
        if (user.isAway()) {
            Label away = new Label(t("away"));
            away.getStyleClass().add("away-badge");
            away.setTooltip(new Tooltip(t("away.tooltip")));
            row.getChildren().add(away);
        }
        if (user.isOutputMuted()) {
            Label mute = new Label(t("audio.off"));
            mute.getStyleClass().add("mute-badge");
            mute.setTooltip(new Tooltip(t("audio.tooltip")));
            row.getChildren().add(mute);
        }
        if (current != null && !current.getServerId().isEmpty()
                && clientVolumeStore.isModified(current.getServerId(), user)) {
            double decibels = clientVolumeStore.get(current.getServerId(), user);
            Label volume = new Label(formatDecibels(decibels));
            volume.getStyleClass().add("volume-badge");
            volume.setTooltip(new Tooltip(t("volume.tooltip", user.getNickname())));
            row.getChildren().add(volume);
        }
        return row;
    }

    private void showUserContextMenu(ClientView user, Node anchor, double screenX, double screenY) {
        if (user == null || current == null || current.getServerId().isEmpty()) return;
        String serverId = current.getServerId();
        double decibels = clientVolumeStore.get(serverId, user);
        ContextMenu menu = new ContextMenu();
        stylePopup(menu);
        MenuItem volume = new MenuItem(t("volume.change"));
        volume.setGraphic(controlIcon(ControlIcon.SPEAKERS, false));
        volume.setOnAction(event -> showUserVolumeDialog(user));
        MenuItem reset = new MenuItem(t("volume.reset.menu"));
        reset.setDisable(Math.abs(decibels) < 0.05D);
        reset.setOnAction(event -> {
            clientVolumeStore.set(serverId, user, 0.0D);
            render(current);
        });
        menu.getItems().addAll(volume, new SeparatorMenuItem(), reset);
        menu.show(anchor, screenX, screenY);
    }

    private void showUserVolumeDialog(ClientView user) {
        if (user == null || current == null || current.getServerId().isEmpty()) return;
        String serverId = current.getServerId();
        double initial = clientVolumeStore.get(serverId, user);
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle(t("volume.title", user.getNickname()));
        dialog.setHeaderText(t("volume.header"));
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        ButtonType cancel = new ButtonType(t("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType accept = new ButtonType(t("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(cancel, accept);

        Slider slider = new Slider(-50.0D, 20.0D, initial);
        slider.setMinWidth(250);
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.setBlockIncrement(1.0D);
        slider.setMajorTickUnit(10.0D);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setAccessibleText(t("volume.accessible", user.getNickname()));
        Label value = new Label(formatDecibels(initial));
        value.getStyleClass().add("volume-value");
        slider.valueProperty().addListener((observable, oldValue, newValue) ->
                value.setText(formatDecibels(newValue.doubleValue())));
        Button restore = new Button(t("volume.restore"));
        restore.setTooltip(new Tooltip(t("volume.restore.tooltip")));
        restore.setOnAction(event -> slider.setValue(0.0D));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(8, 0, 0, 0));
        form.add(new Label(t("volume.field")), 0, 0);
        form.add(slider, 1, 0);
        form.add(value, 2, 0);
        form.add(restore, 1, 1);
        Label help = new Label(t("volume.help"));
        help.getStyleClass().add("muted-text");
        help.setWrapText(true);
        form.add(help, 0, 2, 3, 1);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(78);
        form.getColumnConstraints().add(labelColumn);
        ColumnConstraints sliderColumn = new ColumnConstraints();
        sliderColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().add(sliderColumn);
        form.getColumnConstraints().add(new ColumnConstraints(68));
        pane.setContent(form);
        dialog.setResultConverter(button -> button == accept ? slider.getValue() : null);
        dialog.showAndWait().ifPresent(result -> {
            clientVolumeStore.set(serverId, user, result);
            render(current);
        });
    }

    private static String formatDecibels(double decibels) {
        return String.format(Locale.ROOT, "%+.1f dB", decibels);
    }

    private void renderConnection(GatewaySnapshot snapshot) {
        String server = snapshot.getServerId().isEmpty() ? t("server.none") : snapshot.getServerId();
        switch (snapshot.getStatus()) {
            case CONNECTING:
                statusLabel.setText(t("status.connecting"));
                statusDetailLabel.setText(server);
                break;
            case CONNECTED_IN_CHANNEL:
                statusLabel.setText(t("status.connected.active"));
                statusDetailLabel.setText(t("channels.count", server, current.getChannels().size()));
                break;
            case CONNECTED_NO_CHANNEL:
                statusLabel.setText(t("status.connected.none"));
                statusDetailLabel.setText(server);
                break;
            case ERROR:
                statusLabel.setText(t("status.error"));
                statusDetailLabel.setText(server);
                break;
            default:
                statusLabel.setText(t("status.disconnected"));
                statusDetailLabel.setText(t("status.disconnected.detail"));
                break;
        }
        connectButton.setDisable(snapshot.getStatus() == ConnectionStatus.CONNECTING
                || snapshot.getStatus() == ConnectionStatus.CONNECTED_IN_CHANNEL
                || snapshot.getStatus() == ConnectionStatus.CONNECTED_NO_CHANNEL);
        disconnectButton.setDisable(snapshot.getStatus() == ConnectionStatus.DISCONNECTED
                || snapshot.getStatus() == ConnectionStatus.ERROR);
        renderClientControls(snapshot);
        updateTimers();
    }

    private void renderClientControls(GatewaySnapshot snapshot) {
        ClientView local = snapshot.getClients().get(snapshot.getLocalClientId());
        boolean connected = snapshot.getStatus() == ConnectionStatus.CONNECTED_IN_CHANNEL
                || snapshot.getStatus() == ConnectionStatus.CONNECTED_NO_CHANNEL;
        boolean available = !demoMode && connected && local != null;
        awayButton.setDisable(!available || !gateway.supportsAwayStatus());
        micButton.setDisable(!available || !gateway.supportsMicrophoneMute());
        audioButton.setDisable(!available || !gateway.supportsAudioMute());
        if (local == null) {
            setToggleButton(awayButton, false, t("away.available"), t("away.active"));
            setToggleButton(micButton, false, t("mic.available"), t("mic.active"));
            setToggleButton(audioButton, false, t("audio.available"), t("audio.active"));
            updateTrayMicState(snapshot);
            return;
        }
        setToggleButton(awayButton, local.isAway(), t("away.available"), t("away.active"));
        setToggleButton(micButton, local.isInputMuted(), t("mic.available"), t("mic.active"));
        setToggleButton(audioButton, local.isOutputMuted(), t("audio.available"), t("audio.active"));
        updateTrayMicState(snapshot);
    }

    private void handleMicrophoneLevel(double level) {
        latestMicrophoneLevel = level;
        Platform.runLater(() -> {
            if (audioCaptureMeterControl != null) audioCaptureMeterControl.setProgress(level);
            if (audioCaptureLevelControl != null) audioCaptureLevelControl.setText(audioLevelText(level, true));
            updateTrayMicState(current);
        });
    }

    private void handlePlaybackLevel(double level) {
        latestPlaybackLevel = level;
        Platform.runLater(() -> {
            if (audioPlaybackMeterControl != null) audioPlaybackMeterControl.setProgress(level);
            if (audioPlaybackLevelControl != null) audioPlaybackLevelControl.setText(audioLevelText(level, false));
        });
    }

    private void updateTrayMicState(GatewaySnapshot snapshot) {
        if (desktopTray == null) return;
        ClientView local = snapshot == null ? null
                : snapshot.getClients().get(snapshot.getLocalClientId());
        boolean inVoiceChannel = snapshot != null
                && snapshot.getStatus() == ConnectionStatus.CONNECTED_IN_CHANNEL
                && local != null && local.getChannelId() >= 0;
        if (inVoiceChannel) {
            ChannelView channel = snapshot.getChannels().get(local.getChannelId());
            inVoiceChannel = channel == null || channel.isVoiceCapable();
        }
        if (!inVoiceChannel) {
            desktopTray.setMicState(DesktopTray.MicState.APP);
        } else if (local.isInputMuted()) {
            desktopTray.setMicState(DesktopTray.MicState.MUTED);
        } else if (audioDeviceService != null && audioDeviceService.isVoiceDetected()) {
            desktopTray.setMicState(DesktopTray.MicState.ACTIVE);
        } else {
            desktopTray.setMicState(DesktopTray.MicState.IDLE);
        }
    }

    private String audioLevelText(double level, boolean capture) {
        if (audioDeviceService == null) return t("settings.audio.level.unavailable");
        if (level >= AudioDeviceService.VOICE_ON_THRESHOLD) return t("settings.audio.level.detected");
        return t("settings.audio.level.quiet");
    }

    private void setToggleButton(Button button, boolean active, String inactiveText, String activeText) {
        String description = active ? activeText : inactiveText;
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        ControlIcon icon = button.getUserData() instanceof ControlIcon
                ? (ControlIcon) button.getUserData() : null;
        if (icon != null) button.setGraphic(controlIcon(icon, active));
        if (active) {
            if (!button.getStyleClass().contains("toggle-active")) button.getStyleClass().add("toggle-active");
        } else {
            button.getStyleClass().remove("toggle-active");
        }
    }

    private void toggleAwayStatus() {
        try {
            gateway.toggleAwayStatus();
        } catch (RuntimeException error) {
            addError(error.getMessage());
        }
    }

    private void toggleMicrophoneMute() {
        try {
            gateway.toggleMicrophoneMute();
        } catch (RuntimeException error) {
            addError(error.getMessage());
        }
    }

    private void toggleAudioMute() {
        try {
            gateway.toggleAudioMute();
        } catch (RuntimeException error) {
            addError(error.getMessage());
        }
    }

    private void addError(String message) {
        errorLabel = new Label("!  " + message);
        errorLabel.getStyleClass().add("error-line");
        errorLabel.setWrapText(true);
        if (activeTextRoot != null) {
            int composerIndex = Math.max(0, activeTextRoot.getChildren().size() - 1);
            activeTextRoot.getChildren().add(composerIndex, errorLabel);
        } else {
            mainContent.getChildren().add(errorLabel);
        }
    }

    private void addPermissionsNotice(GatewaySnapshot snapshot) {
        if (snapshot == null || !snapshot.isPermissionsLimited()) return;
        Label notice = new Label(t("connection.limited"));
        notice.getStyleClass().add("notice-line");
        notice.setWrapText(true);
        notice.setMaxWidth(Double.MAX_VALUE);
        if (activeTextRoot != null) {
            int composerIndex = Math.max(0, activeTextRoot.getChildren().size() - 1);
            activeTextRoot.getChildren().add(composerIndex, notice);
        } else {
            mainContent.getChildren().add(notice);
        }
    }

    private void startTimer() {
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTimers()));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    private void updateTimers() {
        Instant now = clock.instant();
        for (Map.Entry<Label, VoiceRoomSession> entry : timerLabels.entrySet()) {
            applyTimerPresentation(entry.getKey(), entry.getValue(), now);
        }
        if (selectedTimerLabel != null) {
            applyTimerPresentation(selectedTimerLabel, sessionFor(current, selectedChannelId), now);
        }
        if (bottomTimerLabel != null) {
            VoiceRoomSession session = sessionFor(current, current == null ? -1 : current.getCurrentChannelId());
            applyTimerPresentation(bottomTimerLabel, session, now);
        }
    }

    private VoiceRoomSession sessionFor(GatewaySnapshot snapshot, int channelId) {
        if (snapshot == null || !snapshot.isSessionStateReady()
                || channelId < 0 || snapshot.getServerId().isEmpty()) return null;
        return snapshot.getSessions().get(new SessionKey(snapshot.getServerId(), channelId));
    }

    private void applyTimerPresentation(Label label, VoiceRoomSession session, Instant now) {
        label.setText(SessionTimerPresentation.value(session, now, language));
        label.setTooltip(new Tooltip(SessionTimerPresentation.tooltip(session, language)));
        if (SessionTimerPresentation.isInherited(session)) {
            if (!label.getStyleClass().contains("unknown-timer")) {
                label.getStyleClass().add("unknown-timer");
            }
        } else {
            label.getStyleClass().remove("unknown-timer");
        }
    }

    private List<ClientView> usersInChannel(GatewaySnapshot snapshot, int channelId) {
        List<ClientView> users = new ArrayList<>();
        for (ClientView client : snapshot.getClients().values()) {
            if (client.getChannelId() == channelId) users.add(client);
        }
        Collections.sort(users, Comparator.comparing(ClientView::getNickname, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    private Node emptyLine(String text) {
        Label line = new Label(text);
        line.getStyleClass().add("empty-line");
        line.setWrapText(true);
        return line;
    }

    private Button iconButton(String glyph, String description, Runnable action) {
        Button button = new Button(glyph);
        button.setMinSize(44, 44);
        button.setMaxSize(44, 44);
        button.setAccessibleText(description);
        button.setTooltip(new Tooltip(description));
        button.setOnAction(event -> action.run());
        button.getStyleClass().add("icon-button");
        return button;
    }

    private Button iconButton(ControlIcon icon, String description, Runnable action) {
        Button button = new Button();
        button.setGraphic(controlIcon(icon, false));
        button.setUserData(icon);
        button.setMinSize(44, 44);
        button.setMaxSize(44, 44);
        button.setAccessibleText(description);
        button.setTooltip(new Tooltip(description));
        button.setOnAction(event -> action.run());
        button.getStyleClass().add("icon-button");
        return button;
    }

    private Node controlIcon(ControlIcon icon, boolean active) {
        SVGPath shape = new SVGPath();
        shape.setContent(controlIconPath(icon, active));
        shape.getStyleClass().add("control-icon-shape");
        StackPane graphic = new StackPane(shape);
        graphic.setMinSize(22, 22);
        graphic.setPrefSize(22, 22);
        graphic.setMaxSize(22, 22);
        graphic.getStyleClass().add("control-icon");
        return graphic;
    }

    private String controlIconPath(ControlIcon icon, boolean active) {
        switch (icon) {
            case AWAY:
                return "M12 3.2a3.2 3.2 0 1 0 0 6.4 3.2 3.2 0 0 0 0-6.4Z"
                        + "M5.2 20.5c.4-3.7 2.7-5.8 6.8-5.8s6.4 2.1 6.8 5.8H5.2Z"
                        + "M18.3 13.1a3.7 3.7 0 1 0 0 7.4 3.7 3.7 0 0 0 0-7.4Zm0 1.2a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5Z"
                        + "M18.3 15v1.9l1.2.8-.6.9-1.8-1.2V15h1.2Z";
            case MICROPHONE:
                return "M12 3a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z"
                        + "M6.5 10.5v.6a5.5 5.5 0 0 0 11 0v-.6h1.5v.6a7 7 0 0 1-6.2 6.9V21h3v1.5H8.2V21h3v-2.1A7 7 0 0 1 5 11.1v-.6h1.5Z"
                        + (active ? "M4 4.5 19.5 20 20.5 19 5 3.5 4 4.5Z" : "");
            case SPEAKERS:
                return "M3.5 9.2h4l5-3.7v13l-5-3.7h-4V9.2Z"
                        + "M15.1 9a4.4 4.4 0 0 1 0 6m2.2-8.3a7.2 7.2 0 0 1 0 10.6"
                        + (active ? "M4 4.5 19.5 20 20.5 19 5 3.5 4 4.5Z" : "");
            default:
                return "";
        }
    }

    private void toggleTheme() {
        lightTheme = !lightTheme;
        if (lightTheme) root.getStyleClass().add("theme-light");
        else root.getStyleClass().remove("theme-light");
        appPreferences.setLightTheme(lightTheme);
    }

    private void showConnectionDialog() {
        Dialog<ConnectionConfig> dialog = new Dialog<>();
        dialog.setTitle(t("connection.title"));
        dialog.setHeaderText(t("connection.header"));
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        ButtonType cancel = new ButtonType(t("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType connect = new ButtonType(t("connect"), ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(cancel, connect);

        ConnectionProfileStore.ConnectionProfile saved = connectionProfileStore.load(defaultStatePath());
        TextField host = new TextField(saved.getHost());
        host.setPromptText(t("connection.host.prompt"));
        host.setAccessibleHelp(t("connection.host.help"));
        TextField port = new TextField(saved.getPort());
        port.setPromptText(t("connection.port.prompt"));
        port.setPrefColumnCount(10);
        port.setTooltip(new Tooltip(t("connection.port.tooltip")));
        port.setAccessibleHelp(t("connection.port.help"));
        TextField nickname = new TextField(saved.getNickname());
        nickname.setAccessibleHelp(t("connection.nickname.help"));
        PasswordField password = new PasswordField();
        password.setText(saved.getPassword());
        password.setAccessibleHelp(t("connection.password.help"));
        TextField state = new TextField(saved.getStatePath().toString());
        state.setPrefColumnCount(30);
        state.setAccessibleHelp(t("connection.state.help"));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(8, 0, 0, 0));
        form.addRow(0, new Label(t("connection.host")), host);
        form.addRow(1, new Label(t("connection.port")), port);
        form.addRow(2, new Label(t("connection.nickname")), nickname);
        form.addRow(3, new Label(t("connection.password")), password);
        form.addRow(4, new Label(t("connection.state")), state);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        form.getColumnConstraints().add(labelColumn);
        form.getColumnConstraints().add(new ColumnConstraints(300));
        pane.setContent(form);

        dialog.setResultConverter(button -> {
            if (button != connect) return null;
            try {
                String stateText = state.getText().trim();
                if (stateText.isEmpty()) throw new IllegalArgumentException(t("connection.state.required"));
                Path statePath = Paths.get(stateText);
                String passwordText = password.getText();
                ConnectionConfig configuration = new ConnectionConfig(host.getText(), port.getText(),
                        passwordText, nickname.getText(), statePath);
                connectionProfileStore.save(host.getText(), port.getText(), nickname.getText(),
                        passwordText, statePath);
                return configuration;
            } catch (RuntimeException error) {
                showInfo(t("invalid.data"), error.getMessage());
                return null;
            }
        });
        // Preserve valid edits even when the user closes the dialog with
        // Cancel or the window close button. Invalid temporary text is ignored
        // so the next dialog still opens with a usable connection profile.
        dialog.setOnHidden(event -> {
            try {
                String stateText = state.getText().trim();
                if (stateText.isEmpty()) return;
                Path statePath = Paths.get(stateText);
                new ConnectionConfig(host.getText(), port.getText(), password.getText(),
                        nickname.getText(), statePath);
                connectionProfileStore.save(host.getText(), port.getText(), nickname.getText(),
                        password.getText(), statePath);
            } catch (RuntimeException ignored) {
                // The result converter already reports invalid OK submissions.
            }
        });
        dialog.showAndWait().ifPresent(configuration -> gateway.connect(configuration));
    }

    private void showSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(t("settings.title"));
        dialog.setHeaderText(t("settings.header"));
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        ButtonType accept = new ButtonType(t("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().add(accept);

        CheckBox startWithWindows = new CheckBox(t("settings.startup"));
        startWithWindows.setSelected(appPreferences.startsWithWindows() || startupManager.isEnabled());
        startWithWindows.setDisable(!startupManager.isSupported());
        startWithWindows.setAccessibleText(t("settings.startup.accessible"));
        CheckBox closeToTray = new CheckBox(t("settings.close.tray"));
        closeToTray.setSelected(appPreferences.closesToTray());
        closeToTray.setAccessibleText(t("settings.close.tray.accessible"));

        Label startHelp = new Label(t("settings.startup.help"));
        startHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(startHelp);
        Label trayHelp = new Label(trayInstalled
                ? t("settings.tray.available") : t("settings.tray.unavailable"));
        trayHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(trayHelp);

        Label languageLabel = new Label(t("settings.language"));
        ComboBox<UiLanguage> languageChoice = new ComboBox<>();
        languageChoice.getItems().addAll(UiLanguage.values());
        languageChoice.setValue(language);
        languageChoice.setMaxWidth(Double.MAX_VALUE);
        languageChoice.setAccessibleText(t("settings.language"));
        Label languageHelp = new Label(t("settings.language.help"));
        languageHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(languageHelp);

        CheckBox voiceNotifications = new CheckBox(t("settings.voice.notifications"));
        voiceNotifications.setSelected(appPreferences.voiceNotifications());
        voiceNotifications.setAccessibleText(t("settings.voice.notifications"));
        Label voiceHelp = new Label(t("settings.voice.help"));
        voiceHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(voiceHelp);

        Label voiceVolumeLabel = new Label(t("settings.voice.volume"));
        voiceVolumeLabel.getStyleClass().add("voice-volume-label");
        Slider voiceVolume = new Slider(0, 100, appPreferences.voiceNotificationVolume());
        voiceVolume.setMinWidth(132);
        voiceVolume.setPrefWidth(160);
        voiceVolume.setBlockIncrement(5);
        voiceVolume.setMajorTickUnit(25);
        voiceVolume.setMinorTickCount(4);
        voiceVolume.setShowTickMarks(true);
        voiceVolume.setShowTickLabels(true);
        voiceVolume.setAccessibleText(t("settings.voice.volume"));
        voiceVolume.setAccessibleHelp(t("settings.voice.volume.help"));
        voiceVolume.setTooltip(new Tooltip(t("settings.voice.volume.help")));
        Label voiceVolumeValue = new Label(formatVoiceVolume((int) Math.round(voiceVolume.getValue())));
        voiceVolumeValue.getStyleClass().add("voice-volume-value");
        voiceVolumeValue.setMinWidth(42);
        voiceVolumeValue.setAlignment(Pos.CENTER_RIGHT);
        voiceVolume.valueProperty().addListener((observable, previous, next) ->
                voiceVolumeValue.setText(formatVoiceVolume((int) Math.round(next.doubleValue()))));
        HBox voiceVolumeRow = new HBox(10, voiceVolumeLabel, voiceVolume, voiceVolumeValue);
        voiceVolumeRow.setAlignment(Pos.CENTER_LEFT);
        voiceVolumeRow.getStyleClass().add("voice-volume-row");
        HBox.setHgrow(voiceVolume, Priority.ALWAYS);
        voiceVolumeRow.disableProperty().bind(voiceNotifications.selectedProperty().not());

        Label audioLabel = new Label(t("settings.audio"));
        audioLabel.getStyleClass().add("settings-section");
        Label audioHelp = new Label(t("settings.audio.help"));
        audioHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(audioHelp);
        Button audioSettingsButton = new Button(t("settings.audio.open"));
        audioSettingsButton.setAccessibleText(t("settings.audio.open.accessible"));
        audioSettingsButton.setTooltip(new Tooltip(t("settings.audio.open.accessible")));
        audioSettingsButton.setOnAction(event -> showAudioSettingsDialog());

        Label themeLabel = new Label(t("settings.theme"));
        Label themeHelp = new Label(t("settings.theme.help"));
        themeHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(themeHelp);

        Label updateLabel = new Label(t("settings.update"));
        updateLabel.getStyleClass().add("settings-section");
        Label versionLabel = new Label(t("settings.version", AppVersion.VERSION));
        versionLabel.getStyleClass().add("muted-text");
        Button updateButton = new Button(t("settings.update.check"));
        updateButton.setAccessibleText(t("settings.update.check.accessible"));
        updateButton.setTooltip(new Tooltip(t("settings.update.check.accessible")));
        ProgressBar updateProgress = new ProgressBar();
        updateProgress.setPrefWidth(124);
        updateProgress.setMinWidth(124);
        updateProgress.setMaxWidth(124);
        updateProgress.setProgress(-1.0D);
        updateProgress.getStyleClass().add("update-progress");
        updateProgress.setVisible(false);
        updateProgress.setManaged(false);
        updateProgress.setAccessibleText(t("settings.update.progress"));
        Label updateStatus = new Label(t("settings.update.help"));
        updateStatus.getStyleClass().add("muted-text");
        constrainSettingsHelp(updateStatus);
        HBox updateRow = new HBox(10, updateButton, updateProgress);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        updateButton.setOnAction(event -> checkForUpdates(updateButton, updateProgress, updateStatus));

        VBox content = new VBox(10, startWithWindows, startHelp, closeToTray, trayHelp,
                voiceNotifications, voiceHelp, voiceVolumeRow, audioLabel, audioHelp,
                audioSettingsButton, themeLabel, themeHelp,
                languageLabel, languageChoice, languageHelp, updateLabel, versionLabel,
                updateRow, updateStatus);
        content.setPadding(new Insets(8, 0, 0, 0));
        pane.setContent(content);
        dialog.setResultConverter(button -> {
            if (button != accept) return null;
            applySettings(startWithWindows.isSelected(), closeToTray.isSelected(),
                    voiceNotifications.isSelected(), (int) Math.round(voiceVolume.getValue()),
                    languageChoice.getValue());
            return null;
        });
        dialog.showAndWait();
    }

    private void showAudioSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(t("settings.audio"));
        dialog.setHeaderText(t("settings.audio.help"));
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(560);
        ButtonType cancel = new ButtonType(t("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType accept = new ButtonType(t("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(cancel, accept);

        List<AudioDevice> captureDevices = localizedAudioDevices(AudioDeviceService.listCaptureDevices());
        List<AudioDevice> playbackDevices = localizedAudioDevices(AudioDeviceService.listPlaybackDevices());
        ComboBox<AudioDevice> captureChoice = new ComboBox<>();
        captureChoice.getItems().addAll(captureDevices);
        captureChoice.setValue(findAudioDevice(captureDevices, appPreferences.captureDevice()));
        captureChoice.setMaxWidth(Double.MAX_VALUE);
        captureChoice.setAccessibleText(t("settings.audio.capture.device"));
        ComboBox<AudioDevice> playbackChoice = new ComboBox<>();
        playbackChoice.getItems().addAll(playbackDevices);
        playbackChoice.setValue(findAudioDevice(playbackDevices, appPreferences.playbackDevice()));
        playbackChoice.setMaxWidth(Double.MAX_VALUE);
        playbackChoice.setAccessibleText(t("settings.audio.playback.device"));
        String initialCapture = appPreferences.captureDevice();
        String initialPlayback = appPreferences.playbackDevice();

        ProgressBar captureMeter = audioMeter(latestMicrophoneLevel, t("settings.audio.capture.level"));
        ProgressBar playbackMeter = audioMeter(latestPlaybackLevel, t("settings.audio.playback.level"));
        Label captureValue = new Label(audioLevelText(latestMicrophoneLevel, true));
        Label playbackValue = new Label(audioLevelText(latestPlaybackLevel, false));
        captureValue.getStyleClass().add("audio-meter-value");
        playbackValue.getStyleClass().add("audio-meter-value");
        captureValue.setMinWidth(74);
        playbackValue.setMinWidth(74);

        Label captureHelp = new Label(t("settings.audio.capture.help"));
        captureHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(captureHelp);
        Label playbackHelp = new Label(t("settings.audio.playback.help"));
        playbackHelp.getStyleClass().add("muted-text");
        constrainSettingsHelp(playbackHelp);

        HBox captureMeterRow = new HBox(10, captureMeter, captureValue);
        captureMeterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(captureMeter, Priority.ALWAYS);
        HBox playbackMeterRow = new HBox(10, playbackMeter, playbackValue);
        playbackMeterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(playbackMeter, Priority.ALWAYS);

        Label captureTitle = new Label("◉  " + t("settings.audio.capture"));
        captureTitle.getStyleClass().add("audio-section-title");
        Label playbackTitle = new Label("◉  " + t("settings.audio.playback"));
        playbackTitle.getStyleClass().add("audio-section-title");
        Label captureDeviceLabel = new Label(t("settings.audio.capture.device"));
        Label playbackDeviceLabel = new Label(t("settings.audio.playback.device"));
        GridPane deviceForm = new GridPane();
        deviceForm.setHgap(12);
        deviceForm.setVgap(8);
        deviceForm.add(captureDeviceLabel, 0, 0);
        deviceForm.add(captureChoice, 1, 0);
        deviceForm.add(playbackDeviceLabel, 0, 1);
        deviceForm.add(playbackChoice, 1, 1);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(132);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        deviceForm.getColumnConstraints().addAll(labelColumn, valueColumn);

        Button testSound = new Button(t("settings.audio.test"));
        testSound.setTooltip(new Tooltip(t("settings.audio.test.tooltip")));
        testSound.setAccessibleText(t("settings.audio.test.tooltip"));
        testSound.setOnAction(event -> {
            AudioDevice selected = playbackChoice.getValue();
            audioDeviceService.playTestTone(selected == null ? "" : selected.getId());
        });

        VBox content = new VBox(10,
                deviceForm,
                new Separator(),
                captureTitle, captureMeterRow, captureHelp,
                new Separator(),
                playbackTitle, playbackMeterRow, playbackHelp, testSound);
        content.setPadding(new Insets(8, 0, 0, 0));
        pane.setContent(content);

        captureChoice.valueProperty().addListener((observable, previous, next) -> {
            if (next != null) audioDeviceService.setCaptureDeviceId(next.getId());
        });
        playbackChoice.valueProperty().addListener((observable, previous, next) -> {
            if (next != null) audioDeviceService.setPlaybackDeviceId(next.getId());
        });
        final boolean[] confirmed = {false};
        dialog.setResultConverter(button -> {
            if (button == accept) {
                confirmed[0] = true;
                AudioDevice capture = captureChoice.getValue();
                AudioDevice playback = playbackChoice.getValue();
                appPreferences.setCaptureDevice(capture == null ? "" : capture.getId());
                appPreferences.setPlaybackDevice(playback == null ? "" : playback.getId());
                audioDeviceService.setCaptureDeviceId(capture == null ? "" : capture.getId());
                audioDeviceService.setPlaybackDeviceId(playback == null ? "" : playback.getId());
            }
            return null;
        });
        audioCaptureMeterControl = captureMeter;
        audioPlaybackMeterControl = playbackMeter;
        audioCaptureLevelControl = captureValue;
        audioPlaybackLevelControl = playbackValue;
        try {
            dialog.showAndWait();
        } finally {
            audioCaptureMeterControl = null;
            audioPlaybackMeterControl = null;
            audioCaptureLevelControl = null;
            audioPlaybackLevelControl = null;
            if (!confirmed[0]) {
                audioDeviceService.setCaptureDeviceId(initialCapture);
                audioDeviceService.setPlaybackDeviceId(initialPlayback);
            }
        }
    }

    private static AudioDevice findAudioDevice(List<AudioDevice> devices, String id) {
        if (devices == null || devices.isEmpty()) return new AudioDevice("", "Default", true);
        String wanted = id == null ? "" : id.trim();
        for (AudioDevice device : devices) {
            if (device.getId().equals(wanted)) return device;
        }
        return devices.get(0);
    }

    private List<AudioDevice> localizedAudioDevices(List<AudioDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return Collections.singletonList(new AudioDevice("", t("settings.audio.default"), true));
        }
        List<AudioDevice> localized = new ArrayList<>(devices.size());
        for (AudioDevice device : devices) {
            localized.add(device.isSystemDefault()
                    ? new AudioDevice(device.getId(), t("settings.audio.default"), true)
                    : device);
        }
        return localized;
    }

    private static ProgressBar audioMeter(double value, String accessibleText) {
        ProgressBar meter = new ProgressBar(Math.max(0.0D, Math.min(1.0D, value)));
        meter.setMinWidth(300);
        meter.setPrefWidth(300);
        meter.setMaxWidth(Double.MAX_VALUE);
        meter.getStyleClass().add("audio-meter");
        meter.setAccessibleText(accessibleText);
        return meter;
    }

    private void checkForUpdates(Button button, ProgressBar progress, Label status) {
        button.setDisable(true);
        status.getStyleClass().remove("error-line");
        progress.setProgress(-1.0D);
        progress.setVisible(true);
        progress.setManaged(true);
        status.setText(t("settings.update.checking"));
        updateExecutor.submit(() -> {
            try {
                UpdateService.UpdateInfo latest = updateService.checkLatest();
                if (!UpdateService.isNewer(latest.getVersion(), AppVersion.VERSION)) {
                    Platform.runLater(() -> {
                        status.getStyleClass().remove("error-line");
                        status.setText(t("settings.update.latest", AppVersion.VERSION));
                        progress.setVisible(false);
                        progress.setManaged(false);
                        button.setDisable(false);
                    });
                    return;
                }

                Platform.runLater(() -> {
                    status.getStyleClass().remove("error-line");
                    status.setText(t("settings.update.available", latest.getVersion()));
                });
                Path installer = updateService.download(latest, fraction -> Platform.runLater(() -> {
                    progress.setProgress(fraction);
                    status.setText(t("settings.update.downloading", latest.getVersion(),
                            (int) Math.round(fraction * 100.0D)));
                }));
                Platform.runLater(() -> launchDownloadedUpdate(installer, button, progress, status));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    progress.setVisible(false);
                    progress.setManaged(false);
                    button.setDisable(false);
                    String message = error.getMessage();
                    status.setText(t("settings.update.error",
                            message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message));
                    status.getStyleClass().add("error-line");
                });
            }
        });
    }

    private void launchDownloadedUpdate(Path installer, Button button,
                                        ProgressBar progress, Label status) {
        try {
            new ProcessBuilder(installer.toAbsolutePath().toString()).start();
            status.getStyleClass().remove("error-line");
            status.setText(t("settings.update.ready"));
            button.setDisable(true);
            progress.setProgress(1.0D);
            exitApplication();
        } catch (Exception error) {
            progress.setVisible(false);
            progress.setManaged(false);
            button.setDisable(false);
            String message = error.getMessage();
            status.setText(t("settings.update.error",
                    message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message));
            status.getStyleClass().add("error-line");
        }
    }

    private void applySettings(boolean startWithWindows, boolean closeToTray,
                               boolean voiceNotifications, int voiceNotificationVolume,
                               UiLanguage selectedLanguage) {
        boolean previousStart = appPreferences.startsWithWindows();
        boolean previousVoiceNotifications = appPreferences.voiceNotifications();
        int previousVoiceNotificationVolume = appPreferences.voiceNotificationVolume();
        UiLanguage previousLanguage = language;
        try {
            if (startWithWindows && !startupManager.isSupported()) {
                throw new IllegalStateException(t("info.startup.error"));
            }
            if (startWithWindows != previousStart || startWithWindows != startupManager.isEnabled()) {
                startupManager.setEnabled(startWithWindows, applicationExecutable());
            }
            appPreferences.setStartsWithWindows(startWithWindows);
            appPreferences.setClosesToTray(closeToTray);
            appPreferences.setVoiceNotifications(voiceNotifications);
            appPreferences.setVoiceNotificationVolume(voiceNotificationVolume);
            if (voiceNotificationService != null) voiceNotificationService.setEnabled(voiceNotifications);
            if (voiceNotificationService != null) {
                voiceNotificationService.setVolumePercent(voiceNotificationVolume);
            }
            UiLanguage nextLanguage = selectedLanguage == null ? UiLanguage.ENGLISH : selectedLanguage;
            appPreferences.setLanguage(nextLanguage);
            if (nextLanguage != previousLanguage) {
                language = nextLanguage;
                if (voiceNotificationService != null) voiceNotificationService.setLanguage(language);
                if (desktopTray != null) desktopTray.setLanguage(language);
                rebuildLocalizedUi();
            }
        } catch (Exception error) {
            appPreferences.setStartsWithWindows(previousStart);
            appPreferences.setVoiceNotifications(previousVoiceNotifications);
            appPreferences.setVoiceNotificationVolume(previousVoiceNotificationVolume);
            if (voiceNotificationService != null) voiceNotificationService.setEnabled(previousVoiceNotifications);
            if (voiceNotificationService != null) {
                voiceNotificationService.setVolumePercent(previousVoiceNotificationVolume);
            }
            appPreferences.setLanguage(previousLanguage);
            language = previousLanguage;
            showInfo(t("info.startup.error"), error.getMessage());
        }
    }

    private void rebuildLocalizedUi() {
        root.setCenter(buildWorkspace());
        root.setBottom(buildConnectionBar());
        render(current);
    }

    private Path applicationExecutable() {
        List<Path> candidates = new ArrayList<>();
        String workingDirectory = System.getProperty("user.dir", "");
        if (!workingDirectory.isEmpty()) {
            candidates.add(Paths.get(workingDirectory, "ts3j-client.exe"));
        }
        try {
            Path location = Paths.get(TeamSpeakDesktopApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
            if (java.nio.file.Files.isDirectory(location)) {
                candidates.add(location.resolve("ts3j-client.exe"));
                if (location.getParent() != null) {
                    candidates.add(location.getParent().resolve("ts3j-client.exe"));
                }
            } else if (location.getParent() != null) {
                candidates.add(location.getParent().resolve("ts3j-client.exe"));
                if (location.getParent().getParent() != null) {
                    candidates.add(location.getParent().getParent().resolve("ts3j-client.exe"));
                }
            }
        } catch (Exception ignored) {
            // Development launchers may not expose a file-backed code source.
        }
        for (Path candidate : candidates) {
            if (candidate != null && java.nio.file.Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Esta opción requiere ejecutar la versión instalada de ts3j-client.");
    }

    private void restoreFromTray() {
        if (desktopTray != null) desktopTray.show();
    }

    private void focusExistingInstance() {
        Platform.runLater(() -> {
            if (stage == null) return;
            if (desktopTray != null) desktopTray.show();
            else stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    private void exitFromInstaller() {
        Platform.runLater(this::exitApplication);
    }

    private void exitApplication() {
        forceExit = true;
        if (desktopTray != null) desktopTray.close();
        Platform.setImplicitExit(true);
        if (stage != null) stage.close();
        Platform.exit();
    }

    private void showInfo(String title, String message) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setContentText(message);
        styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(t("dialog.ok"), ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        if (pane == null) return;
        String stylesheet = getClass().getResource("/com/github/manevolent/ts3j/client/app.css").toExternalForm();
        if (!pane.getStylesheets().contains(stylesheet)) pane.getStylesheets().add(stylesheet);
        pane.getStyleClass().add(lightTheme ? "theme-light" : "theme-dark");
    }

    private void stylePopup(ContextMenu menu) {
        if (menu == null) return;
        menu.getStyleClass().add(lightTheme ? "theme-light" : "theme-dark");
    }

    private static Path defaultStatePath() {
        return Paths.get(System.getProperty("user.home"), ".ts3j-client", "voice-sessions.db");
    }

    private static String initials(String nickname) {
        String trimmed = nickname == null ? "?" : nickname.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public void stop() {
        if (timerTimeline != null) timerTimeline.stop();
        updateExecutor.shutdownNow();
        if (gateway != null) gateway.close();
        if (audioDeviceService != null) audioDeviceService.close();
        if (voiceNotificationService != null) voiceNotificationService.close();
        if (desktopTray != null) desktopTray.close();
        if (singleInstanceGuard != null) singleInstanceGuard.close();
    }
}
