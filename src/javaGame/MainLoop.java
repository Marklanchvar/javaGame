package javaGame;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs a game-loop update every 20 milliseconds (about 50 times per second). */
public class MainLoop implements AutoCloseable {
    private static final long INTERVAL_MS = 20;

    private ScheduledExecutorService executor;

    /** Starts the loop. Override {@link #update()} to perform each iteration. */
    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "game-main-loop");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::update, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Stops the loop. It can be started again afterward. */
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Called once per loop iteration. */
    protected void update() {
        // Add per-frame game logic here.
    }

    @Override
    public void close() {
        stop();
    }
}
