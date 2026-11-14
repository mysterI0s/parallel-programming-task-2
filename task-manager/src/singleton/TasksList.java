package singleton;

import task.Task;

import java.util.ArrayList;
import java.util.List;

public enum TasksList {

    //Singleton
    INSTANCE;

    private List<Task> tasks;

    TasksList() {
        tasks = new ArrayList<>();
    }

    // Both methods are synchronized because every pool thread shares this one
    // list: unsynchronized, concurrent add() calls on an ArrayList lose entries.
    public synchronized void add(Task task) {
        tasks.add(task);
    }

    public synchronized String list() {
        StringBuilder stringBuilder = new StringBuilder();
        tasks.forEach(task -> stringBuilder.append(task));
        return stringBuilder.toString();
    }
}
