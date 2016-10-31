package ru.sbrf;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class ScalableThreadPoolTest {
    @org.junit.Test(expected = IllegalArgumentException.class)
    public void testArguments() throws Exception {
        ThreadPool.createScalable(0, 1);
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void testArguments2() throws Exception {
        ThreadPool.createScalable(1, 0);
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void testArguments3() throws Exception {
        ThreadPool.createScalable(-10, -100);
    }

    @org.junit.Test(expected = IllegalStateException.class)
    public void testExecuteAfterShutdown() throws Exception {
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);
        pool.execute(() -> {
        });
        pool.shutdownNow();
        pool.execute(() -> {
        });
        pool.awaitTermination();
    }

    @org.junit.Test
    public void testEmptyPool() throws Exception {
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);
        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testShutdownEmptyPool() throws Exception {
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        pool.shutdownNow();
        pool.awaitTermination();
        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testSimpleCase() throws Exception {
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 10)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(7);

        assertEquals(10, pool.getCurrentPoolSize());

        pool.shutdownNow();
        pool.awaitTermination();

        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testToMax() throws Exception {

        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 99)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(3);
        assertEquals(50, pool.getCurrentPoolSize());


        TimeUnit.SECONDS.sleep(10);
        pool.shutdownNow();
        pool.awaitTermination();

        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testFromMaxToMin() throws Exception {

        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 50)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(8);
        assertEquals(10, pool.getCurrentPoolSize());

        pool.shutdownNow();
        pool.awaitTermination();

        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testShutdownAfterSubmitTasksBeforeEndAnyTask() throws Exception {

        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 50)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.DAYS.sleep(1);
                    } catch (InterruptedException e) {
                        System.out.println(Thread.currentThread() + ": task has been interrupted.");
                        Thread.currentThread().interrupt();
                        return;
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(3);
        assertEquals(50, pool.getCurrentPoolSize());

        pool.shutdownNow();
        pool.awaitTermination();
        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testShutdownIfTaskDoesNotResetInterruptedStatus() throws Exception {

        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 50)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.DAYS.sleep(1);
                    } catch (InterruptedException e) {
                        System.out.println(Thread.currentThread() + ": task has been interrupted.");
                        return;
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(3);
        assertEquals(50, pool.getCurrentPoolSize());

        pool.shutdownNow();
        pool.awaitTermination();
        assertEquals(0, pool.getCurrentPoolSize());
    }

    @org.junit.Test
    public void testReuse() throws Exception {
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);

        IntStream.range(0, 50)
                .forEach(i -> pool.execute(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        System.out.println(Thread.currentThread() + ": task has been interrupted.");
                        return;
                    }
                    System.out.println(i);
                }));

        TimeUnit.SECONDS.sleep(8);
        assertEquals(10, pool.getCurrentPoolSize());
        assertEquals(10, pool.getCurrentFreeWorkers());

        pool.shutdownNow();
        pool.awaitTermination();
        assertEquals(0, pool.getCurrentPoolSize());
    }


    @org.junit.Test
    public void testExecuteFromManySources() throws Exception {
        ExecutorService clients = Executors.newFixedThreadPool(100);
        ScalableThreadPool pool = (ScalableThreadPool) ThreadPool.createScalable(10, 50);
        final AtomicInteger doneCounter = new AtomicInteger(0);

        IntStream.range(0, 100).forEach(k -> {
            clients.submit(() -> {
                IntStream.range(0, 100).forEach(i -> pool.execute(() ->
                        System.out.println(doneCounter.incrementAndGet())
                ));
            });
        });

        TimeUnit.SECONDS.sleep(5);
        assertEquals(10000, doneCounter.get());
        assertEquals(10, pool.getCurrentPoolSize());
        assertEquals(10, pool.getCurrentFreeWorkers());

        pool.shutdownNow();
        pool.awaitTermination();
        assertEquals(0, pool.getCurrentPoolSize());
    }
}