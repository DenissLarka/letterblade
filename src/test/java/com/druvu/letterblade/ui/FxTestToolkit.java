package com.druvu.letterblade.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit once for the whole test JVM and runs work on the FX thread. Mirrors the druvu-lib-fx
 * FxTestToolkit pattern: the toolkit cannot be restarted after {@code Platform.exit()}, so no test may ever call exit,
 * and implicit exit is disabled so a test that hides its last window does not tear the toolkit down for the rest of the
 * JVM. A task that throws never reaches its {@code countDown}, so the waiting latch times out and fails the test.
 */
final class FxTestToolkit {

    private static final CountDownLatch STARTED = new CountDownLatch(1);
    private static final long TIMEOUT_SECONDS = 10;

    private FxTestToolkit() {}

    static synchronized void ensureStarted() {
        try {
            Platform.startup(STARTED::countDown);
        } catch (IllegalStateException alreadyRunning) {
            STARTED.countDown();
        }
        Platform.setImplicitExit(false);
        awaitLatch(STARTED, "JavaFX toolkit did not start");
    }

    /** Runs {@code action} on the FX thread and blocks until it completes (or fails the test on timeout). */
    static void runAndWait(Runnable action) {
        ensureStarted();
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            action.run();
            done.countDown(); // skipped if action throws -> awaitLatch times out -> the test fails
        });
        awaitLatch(done, "FX task did not finish (it may have thrown)");
    }

    /** Computes a value on the FX thread and returns it to the caller. */
    static <T> T call(Supplier<T> supplier) {
        final AtomicReference<T> result = new AtomicReference<>();
        runAndWait(() -> result.set(supplier.get()));
        return result.get();
    }

    private static void awaitLatch(CountDownLatch latch, String message) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message + " within " + TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the FX thread", ex);
        }
    }
}
