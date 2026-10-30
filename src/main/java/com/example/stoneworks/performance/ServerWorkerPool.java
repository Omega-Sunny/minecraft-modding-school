package com.example.stoneworks.performance;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerWorkerPool {
    private static final AtomicReference<Worker> ACTIVE = new AtomicReference<>();

    private ServerWorkerPool() {}

    public static void onServerStarting(ServerStartingEvent event) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "stoneworks-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );

        Worker previous = ACTIVE.getAndSet(new Worker(event.getServer(), executor));
        if (previous != null) {
            previous.executor().shutdownNow();
        }
    }

    public static Executor executorFor(MinecraftServer server) {
        Worker worker = ACTIVE.get();
        if (worker == null || worker.server() != server || worker.executor().isShutdown()) {
            throw new RejectedExecutionException("Stoneworks worker pool is not active for this server");
        }
        return worker.executor();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        Worker worker = ACTIVE.get();
        if (worker != null && worker.server() == event.getServer() && ACTIVE.compareAndSet(worker, null)) {
            worker.executor().shutdownNow();
        }
    }

    private record Worker(MinecraftServer server, ThreadPoolExecutor executor) {}
}