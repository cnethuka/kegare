package com.cnethuka.kegare.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A TCP proxy to the real database that can be cut and restored: an outage a test can start and end. */
final class ToggleProxy implements AutoCloseable {
    private final String targetHost;
    private final int targetPort;
    private final int port;
    private final List<Socket> open = new CopyOnWriteArrayList<>();
    private volatile ServerSocket listening;

    ToggleProxy() throws IOException {
        String[] target = TestDb.hostAndPort();
        targetHost = target[0];
        targetPort = Integer.parseInt(target[1]);
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        up();
    }

    int port() {
        return port;
    }

    synchronized void up() throws IOException {
        if (listening != null) return;
        ServerSocket socket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
        listening = socket;
        Thread accept = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    Socket client = socket.accept();
                    Socket server = new Socket(targetHost, targetPort);
                    open.add(client);
                    open.add(server);
                    pump(client, server);
                    pump(server, client);
                } catch (IOException e) {
                    return;
                }
            }
        }, "toggle proxy");
        accept.setDaemon(true);
        accept.start();
    }

    /** The outage: nothing connects, and everything open dies. */
    synchronized void down() throws IOException {
        if (listening != null) listening.close();
        listening = null;
        for (Socket socket : open) socket.close();
        open.clear();
    }

    private static void pump(Socket from, Socket to) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
                // cut
            }
            try {
                to.close();
            } catch (IOException ignored) {
                // going anyway
            }
        }, "toggle proxy pump");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() throws IOException {
        down();
    }
}
