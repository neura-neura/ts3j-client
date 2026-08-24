package com.github.manevolent.ts3j.client;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** System-tray integration with the app logo and microphone-state indicator. */
final class DesktopTray implements AutoCloseable {
    enum MicState {
        APP,
        MUTED,
        IDLE,
        ACTIVE
    }

    private TrayIcon trayIcon;
    private Stage stage;
    private Runnable exitAction;
    private UiLanguage language;
    private MenuItem openItem;
    private MenuItem quitItem;
    private MicState micState = MicState.APP;

    DesktopTray() {
        this(UiLanguage.ENGLISH);
    }

    DesktopTray(UiLanguage language) {
        this.language = language == null ? UiLanguage.ENGLISH : language;
    }

    void setLanguage(UiLanguage language) {
        this.language = language == null ? UiLanguage.ENGLISH : language;
        if (openItem != null) openItem.setLabel(UiText.text(this.language, "tray.open"));
        if (quitItem != null) quitItem.setLabel(UiText.text(this.language, "tray.quit"));
        if (trayIcon != null) {
            trayIcon.setToolTip(UiText.text(this.language, "tray.mic." + micState.name().toLowerCase()));
        }
    }

    void setMicState(MicState state) {
        MicState next = state == null ? MicState.APP : state;
        // Audio samples arrive several times per second. Avoid replacing the
        // native tray image when the semantic state did not change; Windows
        // can briefly flash the icon on every setImage call.
        if (micState == next) return;
        micState = next;
        if (trayIcon != null) {
            trayIcon.setImage(createImage(micState));
            trayIcon.setToolTip(UiText.text(language, "tray.mic." + micState.name().toLowerCase()));
        }
    }

    boolean install(Stage stage, Runnable showAction, Runnable exitAction) {
        if (stage == null || showAction == null || exitAction == null || !SystemTray.isSupported()) {
            return false;
        }
        this.stage = stage;
        this.exitAction = exitAction;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem(UiText.text(language, "tray.open"));
            open.addActionListener(event -> Platform.runLater(showAction));
            MenuItem quit = new MenuItem(UiText.text(language, "tray.quit"));
            quit.addActionListener(event -> Platform.runLater(exitAction));
            openItem = open;
            quitItem = quit;
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon icon = new TrayIcon(createImage(micState),
                    UiText.text(language, "tray.mic." + micState.name().toLowerCase()), menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> Platform.runLater(showAction));
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            return true;
        } catch (Exception error) {
            trayIcon = null;
            openItem = null;
            quitItem = null;
            return false;
        }
    }

    void hide() {
        if (stage != null) stage.hide();
    }

    void show() {
        if (stage == null) return;
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    @Override
    public void close() {
        TrayIcon icon = trayIcon;
        trayIcon = null;
        if (icon != null) {
            if (isMacOs()) {
                // Removing an AWT tray icon synchronously from the JavaFX
                // application thread can deadlock inside macOS window disposal.
                // Hand it back to AWT; the explicit quit path then terminates
                // after the remaining app-owned resources have been closed.
                EventQueue.invokeLater(() -> removeTrayIcon(icon));
            } else {
                removeTrayIcon(icon);
            }
        }
        stage = null;
        exitAction = null;
        openItem = null;
        quitItem = null;
    }

    private static void removeTrayIcon(TrayIcon icon) {
        try {
            SystemTray.getSystemTray().remove(icon);
        } catch (Exception ignored) { }
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private static BufferedImage createImage(MicState state) {
        Dimension size = SystemTray.isSupported()
                ? SystemTray.getSystemTray().getTrayIconSize() : new Dimension(16, 16);
        int width = Math.max(16, size.width);
        int height = Math.max(16, size.height);
        if (state == MicState.APP) {
            BufferedImage appIcon = loadAppIcon();
            if (appIcon != null) {
                BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(appIcon, 0, 0, width, height, null);
                    return scaled;
                } finally {
                    graphics.dispose();
                }
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color;
            if (state == MicState.MUTED) color = new Color(207, 62, 83, 255);
            else if (state == MicState.ACTIVE) color = new Color(55, 185, 131, 255);
            else color = new Color(154, 160, 178, 255);
            int diameter = Math.max(8, Math.min(width, height) - 4);
            int x = (width - diameter) / 2;
            int y = (height - diameter) / 2;
            graphics.setColor(color);
            graphics.fillOval(x, y, diameter, diameter);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage loadAppIcon() {
        try (InputStream stream = DesktopTray.class.getResourceAsStream(
                "/com/github/manevolent/ts3j/client/ts3j-client.png")) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (Exception ignored) {
            return null;
        }
    }
}
