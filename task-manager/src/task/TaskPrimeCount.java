package task;

/**
 * Counts the prime numbers inside a range.
 *
 * Input: "N"       -> primes in [1, N]
 *        "FROM-TO" -> primes in [FROM, TO], both ends included
 *
 * The check is plain trial division on purpose: the task is CPU-bound and its
 * running time grows with the range, so a few requests sent together are enough
 * to watch the server's thread pool run them side by side.
 */
public class TaskPrimeCount extends TaskImpl {

    /** Largest number accepted, so a single request cannot occupy a pool thread indefinitely. */
    public static final int MAX_NUMBER = 100_000_000;

    private static final String USAGE =
            "input must be N or FROM-TO, using whole numbers from 0 to " + MAX_NUMBER;

    @Override
    public void execute() {
        try {
            int[] range = parseRange(input);
            int count = countPrimes(range[0], range[1]);
            result = count + " primes in [" + range[0] + ", " + range[1] + "]";
        } catch (IllegalArgumentException e) {
            result = "Exception: " + e.getMessage();
        }
    }

    private static int[] parseRange(String text) {
        if (text == null) {
            throw new IllegalArgumentException(USAGE);
        }
        String[] parts = text.split("-", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException(USAGE);
        }

        int from, to;
        try {
            from = parts.length == 2 ? Integer.parseInt(parts[0].trim()) : 1;
            to = Integer.parseInt(parts[parts.length - 1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(USAGE);
        }

        if (to > MAX_NUMBER) {
            throw new IllegalArgumentException("largest allowed number is " + MAX_NUMBER);
        }
        if (from > to) {
            throw new IllegalArgumentException("range start " + from + " is after range end " + to);
        }
        return new int[] {from, to};
    }

    private static int countPrimes(int from, int to) {
        int count = 0;
        for (int n = from; n <= to; n++) {
            if (isPrime(n)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        if (n < 4) {
            return true; // 2 and 3
        }
        if (n % 2 == 0 || n % 3 == 0) {
            return false;
        }
        // Every prime above 3 is of the form 6k-1 or 6k+1, so only those divisors are tried.
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }
}
