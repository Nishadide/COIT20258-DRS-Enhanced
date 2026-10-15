package com.cqu.drs.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accepts client connections and gives each one a thread.
 *
 * <h2>The concurrency model</h2>
 * One thread per connected client, drawn from a pool of a fixed size. The
 * accept loop runs on its own thread and does nothing but accept and hand off,
 * so a slow client cannot stop a new one connecting.
 *
 * <p>The pool is bounded rather than unbounded. {@code newCachedThreadPool}
 * would create a thread for every connection, so a client opening connections
 * in a loop could exhaust memory, and nothing in the protocol stops it doing
 * so. With a bounded pool the surplus connections wait instead, which is a
 * delay rather than a failure.
 *
 * <p>The cost of thread-per-client is that a connection occupies a thread even
 * while idle, so the pool size is also the number of clients that can be
 * connected at once. For this application that is the right trade: a disaster
 * response system has tens of coordinators and responders, not thousands, and
 * thread-per-client is far simpler to reason about than the alternative, and
 * simpler to show working.
 *
 * <h2>Why no locks</h2>
 * Handlers share nothing mutable. Session state lives in a ConcurrentHashMap
 * and everything else lives in the database, where two clients competing for
 * the same row are ordered by a transaction rather than by a Java lock. A
 * {@code synchronized} block would protect one server process only, and a
 * design that is correct only while exactly one server process exists is not a
 * three-tier design.
 */
public final class DrsServer {

    private final int requestedPort;
    private final int poolSize;
    private final RequestDispatcher dispatcher;

    private final AtomicLong connectionsAccepted = new AtomicLong();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService pool;
    private volatile Thread acceptThread;
    private volatile boolean running;

    /**
     * Creates a server.
     *
     * @param port       the port to listen on, or 0 to let the operating
     *                   system choose a free one
     * @param poolSize   how many clients may be served at once
     * @param dispatcher where requests are sent
     */
    public DrsServer(int port, int poolSize, RequestDispatcher dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("A server needs a dispatcher.");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("The thread pool must hold at least one thread.");
        }
        this.requestedPort = port;
        this.poolSize = poolSize;
        this.dispatcher = dispatcher;
    }

    /**
     * Binds the port and starts accepting connections.
     *
     * Returns as soon as the port is bound rather than blocking, so that the
     * caller can report the port and, in a test, connect to it immediately.
     *
     * @throws IOException when the port cannot be bound, usually because
     *                     something else is already using it
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("The server is already running.");
        }

        ServerSocket socket = new ServerSocket();
        // Without this, restarting the server can fail for a minute or two
        // while the operating system holds the previous socket in TIME_WAIT.
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(requestedPort));

        this.serverSocket = socket;
        this.pool = Executors.newFixedThreadPool(poolSize, namedThreads());
        this.running = true;

        this.acceptThread = new Thread(this::acceptLoop, "drs-accept");
        this.acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                connectionsAccepted.incrementAndGet();
                pool.submit(new ClientHandler(client, dispatcher));

            } catch (IOException e) {
                // Closing the server socket makes the blocked accept throw.
                // That is how the loop is stopped, so it is not an error
                // unless the server is still meant to be running.
                if (running) {
                    System.err.println("Could not accept a connection: " + e.getMessage());
                }
            } catch (RejectedExecutionException e) {
                // The pool was shut down between accepting and submitting.
                return;
            }
        }
    }

    /**
     * Stops accepting connections and closes the ones in progress.
     *
     * Safe to call more than once, and safe to call on a server that was never
     * started.
     */
    public void stop() {
        running = false;

        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                // This makes the blocked accept call throw, which is the only
                // way to interrupt it.
                socket.close();
            } catch (IOException e) {
                System.err.println("The server socket did not close cleanly: "
                        + e.getMessage());
            }
        }

        ExecutorService executor = this.pool;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Some client threads did not stop within 5 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Thread accepter = this.acceptThread;
        if (accepter != null) {
            try {
                accepter.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * The port the server is actually listening on.
     *
     * This is not always the port asked for. A server constructed with port 0
     * is given a free port by the operating system, which is how the tests
     * avoid failing when the configured port happens to be in use on the
     * machine running them.
     *
     * @return the bound port, or -1 when the server is not running
     */
    public int getPort() {
        ServerSocket socket = this.serverSocket;
        return socket == null || socket.isClosed() ? -1 : socket.getLocalPort();
    }

    /**
     * Whether the server is accepting connections.
     *
     * @return true while running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * How many connections have been accepted since the server started.
     *
     * @return the count
     */
    public long getConnectionsAccepted() {
        return connectionsAccepted.get();
    }

    /**
     * How many clients may be served at once.
     *
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Names the pool's threads, so that a thread dump taken while diagnosing a
     * problem says which threads belong to this server instead of showing a
     * row of identical default names.
     *
     * @return the thread factory
     */
    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "drs-client-" + counter.incrementAndGet());
            // Daemon threads do not keep the JVM alive, so a server that has
            // been stopped does not leave the process running.
            thread.setDaemon(true);
            return thread;
        };
    }
}
