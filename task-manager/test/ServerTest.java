import handler.RequestsHandlerSocket;
import singleton.TasksList;
import task.TaskNotFound;
import util.Log;
import util.PoolFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dependency-free tests for the server-side changes.
 *
 * Run from task-manager/:
 *   javac -d out -sourcepath src src/*.java src/task/*.java test/*.java
 *   java -cp out ServerTest
 */
public class ServerTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        socketHandlerAnswersWithValidHttp();
        logLinesNameTheThreadThatWroteThem();
        poolSizeSelectsFixedOrCachedPool();
        taskHistoryKeepsEveryTaskAddedConcurrently();

        if (failures > 0) {
            System.out.println("\n" + failures + " test(s) FAILED");
            System.exit(1);
        }
        System.out.println("\nAll tests passed");
    }

    /** Sends a real POST over a real socket and inspects the raw bytes that come back. */
    private static void socketHandlerAnswersWithValidHttp() throws Exception {
        String requestBody = "TaskPrimeCount&100";
        String request = "POST / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + requestBody.length() + "\r\n"
                + "\r\n"
                + requestBody;

        String raw;
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("localhost", server.getLocalPort())) {
            Thread handler = new Thread(new RequestsHandlerSocket(server.accept()));
            handler.start();
            client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();
            raw = readAll(client.getInputStream());
            handler.join();
        }

        check("socket response starts with an HTTP status line",
                raw.startsWith("HTTP/1.1 200 OK\r\n"), raw);

        int headerEnd = raw.indexOf("\r\n\r\n");
        String headers = headerEnd < 0 ? "" : raw.substring(0, headerEnd);
        String body = headerEnd < 0 ? "" : raw.substring(headerEnd + 4);

        check("socket response body carries the task result",
                body.startsWith("Result: 25 primes in [1, 100]; Executed in: "), raw);
        check("socket response declares the exact body length",
                headers.contains("\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length),
                raw);
    }

    private static void logLinesNameTheThreadThatWroteThem() throws Exception {
        String[] line = new String[1];
        Thread worker = new Thread(() -> line[0] = Log.format("hello"), "worker-7");
        worker.start();
        worker.join();

        check("log line is 'HH:mm:ss.SSS [thread-name] message'",
                line[0].matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[worker-7\\] hello"), line[0]);
    }

    private static void poolSizeSelectsFixedOrCachedPool() {
        String fixed = poolBounds("4");
        check("POOL_SIZE=4 gives a pool fixed at 4 threads", fixed.equals("4..4"), fixed);

        String unbounded = "0.." + Integer.MAX_VALUE;
        for (String value : new String[] {null, "", "0", "-2", "abc"}) {
            String cached = poolBounds(value);
            check("POOL_SIZE=" + value + " keeps the original cached pool", cached.equals(unbounded), cached);
        }
    }

    /** Describes a pool as "corePoolSize..maximumPoolSize". */
    private static String poolBounds(String poolSize) {
        ExecutorService pool = PoolFactory.create(poolSize);
        try {
            if (!(pool instanceof ThreadPoolExecutor)) {
                return pool.getClass().getName();
            }
            ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
            return executor.getCorePoolSize() + ".." + executor.getMaximumPoolSize();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Every pool thread appends its finished task to the shared TasksList, so
     * concurrent add() calls must not lose entries or corrupt the list.
     */
    private static void taskHistoryKeepsEveryTaskAddedConcurrently() throws Exception {
        int threads = 8;
        int addsPerThread = 5000;
        int before = lineCount(TasksList.INSTANCE.list());

        CountDownLatch startTogether = new CountDownLatch(1);
        Thread[] writers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            writers[i] = new Thread(() -> {
                try {
                    startTogether.await();
                    for (int n = 0; n < addsPerThread; n++) {
                        TasksList.INSTANCE.add(new TaskNotFound());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    // A corrupted list can throw; the count check below reports it.
                }
            });
            writers[i].start();
        }
        startTogether.countDown();
        for (Thread writer : writers) {
            writer.join();
        }

        int added = lineCount(TasksList.INSTANCE.list()) - before;
        check("task history keeps all " + (threads * addsPerThread) + " tasks added by " + threads + " threads",
                added == threads * addsPerThread, "only " + added + " were kept");
    }

    private static int lineCount(String text) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void check(String name, boolean passed, String actual) {
        if (passed) {
            System.out.println("PASS  " + name);
        } else {
            failures++;
            System.out.println("FAIL  " + name + "\n      got: " + String.valueOf(actual).trim());
        }
    }
}
