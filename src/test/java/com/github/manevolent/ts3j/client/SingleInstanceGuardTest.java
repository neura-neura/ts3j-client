package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleInstanceGuardTest {
    @Test
    public void secondInstanceSignalsFocusInsteadOfAcquiringTheLock() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-single-instance-");
        SingleInstanceGuard first = new SingleInstanceGuard(directory);
        SingleInstanceGuard second = new SingleInstanceGuard(directory);
        CountDownLatch focus = new CountDownLatch(1);
        try {
            assertTrue(first.acquire(focus::countDown));
            assertFalse(second.acquire(() -> { }));
            assertTrue(focus.await(2L, TimeUnit.SECONDS));
        } finally {
            second.close();
            first.close();
            Files.deleteIfExists(directory.resolve("instance.port"));
            Files.deleteIfExists(directory.resolve("instance.lock"));
            Files.deleteIfExists(directory);
        }
    }
}
