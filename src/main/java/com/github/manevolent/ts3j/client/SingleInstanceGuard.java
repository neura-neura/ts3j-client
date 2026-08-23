package com.github.manevolent.ts3j.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Per-user single-instance guard. The lock handles stale processes safely;
 * the loopback socket lets a later launch ask the owner to restore its window.
 */
final class SingleInstanceGuard implements AutoCloseable {
    private final Path lockPath;
    private final Path portPath;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private Runnable focusAction;
    private Runnable exitAction;
    private volatile boolean closed;
    private boolean owner;

    SingleInstanceGuard(Path stateDirectory) {
        if (stateDirectory == null) throw new IllegalArgumentException("stateDirectory");
        this.lockPath = stateDirectory.resolve("instance.lock");
        this.portPath = stateDirectory.resolve("instance.port");
    }

    synchronized boolean acquire(Runnable focusAction) throws IOException {
        return acquire(focusAction, null);
    }

    synchronized boolean acquire(Runnable focusAction, Runnable exitAction) throws IOException {
        if (owner) return true;
        this.focusAction = focusAction;
        this.exitAction = exitAction;
        Files.createDirectories(lockPath.getParent());
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            fileLock = lockChannel.tryLock();
        } catch (OverlappingFileLockException ignored) {
            fileLock = null;
        }
        if (fileLock == null) {
            notifyExistingInstance("focus");
            releaseResources(false);
            return false;
        }

        try {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Files.write(portPath,
                    Integer.toString(serverSocket.getLocalPort()).getBytes(StandardCharsets.US_ASCII),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            owner = true;
            closed = false;
            listenerThread = new Thread(this::listenForFocusRequests, "ts3j-client-instance");
            listenerThread.setDaemon(true);
            listenerThread.start();
            return true;
        } catch (IOException error) {
            releaseResources(false);
            throw error;
        }
    }

    /** Sends the graceful shutdown command used by the installer. */
    boolean requestExit() {
        return notifyExistingInstance("exit");
    }

    private void listenForFocusRequests() {
        while (!closed) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(1000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                if ("focus".equalsIgnoreCase(request) && focusAction != null) {
                    focusAction.run();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.US_ASCII));
                    writer.println("ok");
                    writer.flush();
                } else if ("exit".equalsIgnoreCase(request) && exitAction != null) {
                    exitAction.run();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.US_ASCII));
                    writer.println("ok");
                    writer.flush();
                }
            } catch (IOException error) {
                if (!closed) {
                    // The socket is local and short-lived; retry until shutdown.
                }
            } catch (RuntimeException error) {
                // A focus request must never terminate the listener thread.
            }
        }
    }

    private boolean notifyExistingInstance(String request) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                if (Files.exists(portPath)) {
                    String value = new String(Files.readAllBytes(portPath), StandardCharsets.US_ASCII).trim();
                    int port = Integer.parseInt(value);
                    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
                        socket.setSoTimeout(1000);
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.US_ASCII));
                        writer.println(request);
                        writer.flush();
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // The owner may still be publishing its ephemeral port.
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    @Override
    public synchronized void close() {
        if (!owner && lockChannel == null) return;
        closed = true;
        releaseResources(owner);
        owner = false;
    }

    private void releaseResources(boolean deletePort) {
        if (deletePort) {
            try {
                Files.deleteIfExists(portPath);
            } catch (IOException ignored) { }
        }
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }
        Thread listener = listenerThread;
        listenerThread = null;
        if (listener != null && listener != Thread.currentThread()) listener.interrupt();
        if (fileLock != null) {
            try {
                fileLock.release();
            } catch (IOException ignored) { }
            fileLock = null;
        }
        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException ignored) { }
            lockChannel = null;
        }
        focusAction = null;
        exitAction = null;
    }
}
