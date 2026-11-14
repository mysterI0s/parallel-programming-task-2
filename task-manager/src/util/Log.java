package util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Console logging that stamps every line with the time and the name of the
 * thread that wrote it, so the output shows which pool thread ran which task.
 */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() { }

    public static void info(String message) {
        System.out.println(format(message));
    }

    public static String format(String message) {
        return LocalTime.now().format(TIME) + " [" + Thread.currentThread().getName() + "] " + message;
    }
}
