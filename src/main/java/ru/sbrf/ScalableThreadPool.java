package ru.sbrf;

import java.util.*;

class ScalableThreadPool implements ThreadPool {
    private final int minWorkers;
    private final int maxWorkers;
    private final Queue<Runnable> tasks = new LinkedList<>();
    private final Set<Worker> workers = new HashSet<>();
    private int freeWorkers = 0;
    private boolean isRunning = true;

    ScalableThreadPool(int minWorkers, int maxWorkers) {
        if (minWorkers < 1 || maxWorkers < minWorkers) {
            throw new IllegalArgumentException();
        }
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
    }

    @Override
    public void execute(Runnable task) {
        if (!isRunning) {
            throw new IllegalStateException("Pool is in teardown state.");
        }
        Objects.requireNonNull(task);
        synchronized (tasks) {
            if (!isRunning) {
                throw new IllegalStateException("Pool is in teardown state.");
            }
            if (addNewIfNeedWorker(task)) {
                return;
            }
            tasks.add(task);
            tasks.notify();
        }
    }

    private boolean addNewIfNeedWorker(Runnable task) {
        synchronized (workers) {
            if (freeWorkers == 0 && workers.size() < maxWorkers) {
                final Worker w = new Worker(task);
                workers.add(w);
                w.start();
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdownNow() {
        synchronized (tasks) {
            isRunning = false;
            synchronized (workers) {
                workers.forEach(Worker::interrupt);
            }
        }
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        if (isRunning) {
            throw new IllegalStateException("Pool is running!");
        }
        List<Worker> aliveWorkers;
        synchronized (workers) {
            aliveWorkers = new ArrayList<>(workers);
        }
        for (Worker w : aliveWorkers) {
            w.join();
        }
    }

    int getCurrentPoolSize() {
        synchronized (workers) {
            return workers.size();
        }
    }

    int getCurrentFreeWorkers() {
        synchronized (workers) {
            return freeWorkers;
        }
    }

    private void incFreeWorkers() {
        synchronized (workers) {
            freeWorkers++;
        }
    }

    private void decFreeWorkers() {
        synchronized (workers) {
            freeWorkers--;
        }
    }

    private class Worker extends Thread {
        private final Runnable firstTask;

        Worker(Runnable firstTask) {
            super();
            this.firstTask = firstTask;
        }

        @Override
        public void run() {
            Optional<Runnable> task = Optional.of(firstTask);
            while (!Thread.currentThread().isInterrupted() && isRunning) { // pool is turning down
                if (!task.isPresent()) {
                    task = nextTask();
                    if (!task.isPresent()) {
                        return;
                    }
                }
                try {
                    task.ifPresent(Runnable::run);
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    task = Optional.empty();
                    incFreeWorkers();
                }
            }
            // When first task is canceled right now before execute - freeWorkers is decreased and fall in inconsistent state
            // but it is not problem because it dose not matter, not any new task could not be executed
            cleanResources();
        }

        private Optional<Runnable> nextTask() {
            Runnable task;
            synchronized (tasks) {
                while ((task = tasks.poll()) == null) {
                    synchronized (workers) {
                        if (workers.size() > minWorkers) { // there is not any task and this worker is unnecessary
                            cleanResources();
                            return Optional.empty();
                        }
                    }
                    try {
                        tasks.wait();
                    } catch (InterruptedException e) { // pool is turning down
                        cleanResources();
                        return Optional.empty();
                    }
                }
                // Some problem exist: more than one thread do execute sequentially rely on freeWorkers, before this thread exit from wait()
                decFreeWorkers();
            }
            return Optional.of(task);
        }


        private void cleanResources() {
            synchronized (workers) {
                workers.remove(this);
                decFreeWorkers();
            }
        }
    }
}
