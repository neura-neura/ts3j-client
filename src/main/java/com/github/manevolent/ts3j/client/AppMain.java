package com.github.manevolent.ts3j.client;

import javafx.application.Application;

/** Entry point for the optional JavaFX desktop client. */
public final class AppMain {
    private AppMain() { }

    public static void main(String[] args) {
        Application.launch(TeamSpeakDesktopApp.class, args);
    }
}
