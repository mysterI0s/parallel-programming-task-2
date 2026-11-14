import singleton.TaskExecutor;
import task.Task;
import task.TaskPrimeCount;

/**
 * Dependency-free tests for TaskPrimeCount (the project has no build tool, so no JUnit).
 *
 * Run from task-manager/:
 *   javac -d out -sourcepath src src/*.java src/task/*.java test/*.java
 *   java -cp out TaskPrimeCountTest
 */
public class TaskPrimeCountTest {

    private static int failures = 0;

    public static void main(String[] args) {
        // A single number N means the range [1, N].
        expectResult("single number counts primes up to N", "100", "25 primes in [1, 100]");
        expectResult("range FROM-TO is inclusive on both ends", "10-20", "4 primes in [10, 20]");
        expectResult("range holding exactly one prime", "13-13", "1 primes in [13, 13]");
        expectResult("range holding no primes", "14-16", "0 primes in [14, 16]");
        expectResult("0 and 1 are not prime", "0-1", "0 primes in [0, 1]");
        expectResult("2 is the smallest prime", "2-2", "1 primes in [2, 2]");
        expectResult("squares of primes are not prime", "25-49", "6 primes in [25, 49]");
        expectResult("known value: pi(1,000,000) = 78498", "1-1000000", "78498 primes in [1, 1000000]");
        expectResult("spaces around the numbers are ignored", " 10 - 20 ", "4 primes in [10, 20]");

        expectError("text instead of a number", "abc");
        expectError("empty input", "");
        expectError("missing input", null);
        expectError("range start after range end", "20-10");
        expectError("single number below 1 (the range [1, 0] is empty)", "0");
        expectError("negative number", "-5");
        expectError("too many parts", "1-2-3");
        expectError("upper bound above the allowed maximum", "1-100000001");

        // The server finds tasks by class name through reflection, so this is the
        // real integration point: "TaskName&input" exactly as it arrives in a POST body.
        String viaServer = TaskExecutor.INSTANCE.run("TaskPrimeCount&100");
        check("server dispatcher finds and runs the task by name",
                viaServer.startsWith("Result: 25 primes in [1, 100]; Executed in: "), viaServer);

        if (failures > 0) {
            System.out.println("\n" + failures + " test(s) FAILED");
            System.exit(1);
        }
        System.out.println("\nAll tests passed");
    }

    private static String run(String input) {
        Task task = new TaskPrimeCount();
        task.setInput(input);
        task.execute();
        task.setExecutedTime("0.00s");
        return task.getResult();
    }

    private static void expectResult(String name, String input, String expected) {
        String actual = run(input);
        check(name, actual.equals("Result: " + expected + "; Executed in: 0.00s\n"), actual);
    }

    private static void expectError(String name, String input) {
        String actual = run(input);
        check("rejects " + name, actual.startsWith("Result: Exception: "), actual);
    }

    private static void check(String name, boolean passed, String actual) {
        if (passed) {
            System.out.println("PASS  " + name);
        } else {
            failures++;
            System.out.println("FAIL  " + name + "\n      got: " + actual.trim());
        }
    }
}
