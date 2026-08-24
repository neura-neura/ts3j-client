package com.github.manevolent.ts3j.client;

/** Release metadata for the optional desktop client. */
final class AppVersion {
    static final String PRODUCT_NAME = "ts3j-client";
    static final String VERSION = "1.0.14";
    static final String GITHUB_REPOSITORY = "neura-neura/ts3j-client";
    static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + GITHUB_REPOSITORY + "/releases/latest";

    private AppVersion() { }
}
