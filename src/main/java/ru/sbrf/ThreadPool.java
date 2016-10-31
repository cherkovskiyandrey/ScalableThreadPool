package ru.sbrf;

public interface ThreadPool {
    void execute(Runnable task);

    void shutdownNow();

    void awaitTermination() throws InterruptedException;

    static ThreadPool createScalable(int min, int max) {
        return new ScalableThreadPool(min, max);
    }
}
