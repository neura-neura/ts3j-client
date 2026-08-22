package com.github.manevolent.ts3j.client;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** System-tray integration using the app icon with a generated fallback. */
final class DesktopTray implements AutoCloseable {
    private TrayIcon trayIcon;
    private Stage stage;
    private Runnable exitAction;
    private UiLanguage language;
    private MenuItem openItem;
    private MenuItem quitItem;

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
        if (trayIcon != null) trayIcon.setToolTip(UiText.text(this.language, "server"));
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

            TrayIcon icon = new TrayIcon(createImage(), UiText.text(language, "server"), menu);
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
            try {
                SystemTray.getSystemTray().remove(icon);
            } catch (Exception ignored) { }
        }
        stage = null;
        exitAction = null;
        openItem = null;
        quitItem = null;
    }

    private static BufferedImage createImage() {
        Dimension size = SystemTray.isSupported()
                ? SystemTray.getSystemTray().getTrayIconSize() : new Dimension(16, 16);
        int width = Math.max(16, size.width);
        int height = Math.max(16, size.height);
        try (InputStream stream = DesktopTray.class.getResourceAsStream(
                "/com/github/manevolent/ts3j/client/ts3j-client.png")) {
            if (stream != null) {
                BufferedImage source = ImageIO.read(stream);
                if (source != null) {
                    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D graphics = scaled.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(source, 0, 0, width, height, null);
                    } finally {
                        graphics.dispose();
                    }
                    return scaled;
                }
            }
        } catch (Exception ignored) {
            // Use the generated fallback below when the packaged resource is unavailable.
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(25, 31, 42, 255));
            graphics.fillRoundRect(1, 1, width - 2, height - 2, width / 3, height / 3);
            graphics.setColor(new Color(156, 196, 255, 255));
            graphics.fillRoundRect(width / 3, height / 5, width / 3, height * 3 / 5, 2, 2);
            graphics.fillRoundRect(width / 5, height / 5, width * 3 / 5, Math.max(2, height / 7), 2, 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
