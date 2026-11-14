package util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the thread pool both web servers hand their requests to.
 */
public final class PoolFactory {

    public static final String POOL_SIZE_ENV = "POOL_SIZE";

    private PoolFactory() { }

    /** Pool selected by the POOL_SIZE environment variable. */
    public static ExecutorService fromEnvironment() {
        return create(System.getenv(POOL_SIZE_ENV));
    }

    /**
     * @param poolSize a positive number gives a fixed pool of that many threads;
     *                 anything else (unset, empty, 0, not a number) keeps the
     *                 project's original cached pool
     */
    public static ExecutorService create(String poolSize) {
        int threads = parseThreads(poolSize);
        if (threads > 0) {
            Log.info("Thread pool: FIXED with " + threads + " thread(s), extra requests wait in its queue");
            return Executors.newFixedThreadPool(threads);
        }
        Log.info("Thread pool: CACHED, creates a new thread whenever all existing ones are busy");
        return Executors.newCachedThreadPool();
    }

    private static int parseThreads(String poolSize) {
        try {
            return poolSize == null ? 0 : Integer.parseInt(poolSize.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
