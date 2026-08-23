package com.github.manevolent.ts3j.client;

import javafx.application.Application;

import java.nio.file.Paths;

/** Entry point for the optional JavaFX desktop client. */
public final class AppMain {
    private AppMain() { }

    public static void main(String[] args) {
        if (contains(args, "--shutdown")) {
            SingleInstanceGuard guard = new SingleInstanceGuard(Paths.get(
                    System.getProperty("user.home"), ".ts3j-client"));
            guard.requestExit();
            guard.close();
            return;
        }
        Application.launch(TeamSpeakDesktopApp.class, args);
    }

    private static boolean contains(String[] args, String expected) {
        if (args == null) return false;
        for (String arg : args) if (expected.equalsIgnoreCase(arg)) return true;
        return false;
    }
}
