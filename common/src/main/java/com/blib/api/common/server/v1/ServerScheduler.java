package com.blib.api.common.server.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Runs deferred tasks on the server thread.
 * <p>
 * This class is ticked from {@code postLevelTick}, which fires for <em>every</em> level on <em>both</em> logical sides.
 * In a single-player session the client and the integrated server share one JVM, and therefore one copy of the static
 * task queue below, so every guard in {@link #tick(Level)} matters:
 * <ul>
 * <li>Client levels are rejected outright. Without this the render thread drains the same queue as the server thread
 * and runs server-side work — block writes, particle broadcasts, entity removal — off the server thread, on data
 * structures (chunk storage, the lighting engine, chunk map player tracking) that are single-thread-affine. That is how
 * a scheduled task can hang a client indefinitely while the server carries on ticking underneath it.</li>
 * <li>Only one drain happens per server tick, not one per level. A server with three dimensions calls this method three
 * times a tick.</li>
 * <li>Due tasks are collected first and run afterwards. A task that schedules a follow-up task — which is exactly what
 * a multi-cycle explosion does — must not have that follow-up picked up by the same drain. Mutating the backing
 * collection mid-traversal is what made scheduled delays unreliable before.</li>
 * </ul>
 * <p>
 * Delays are counted in server ticks rather than wall-clock milliseconds, so a delay no longer keeps elapsing while the
 * game is paused and does not fire a backlog of tasks all at once on unpause.
 */
public class ServerScheduler {

    private static final long MILLISECONDS_PER_TICK = 50L;

    private static final Queue<ScheduledTask> SCHEDULED_TASKS = new ConcurrentLinkedQueue<>();

    /**
     * The last server tick this scheduler observed. Volatile because {@link #schedule} may be called from a thread
     * other than the one that advances it.
     */
    private static volatile long currentTick = 0L;

    /**
     * The server the queued tasks belong to. When it changes — a world was left and another loaded — the queue is
     * dropped rather than replayed against a server the tasks know nothing about.
     */
    private static MinecraftServer owner = null;

    /**
     * Queues a task to run on the server thread after the given delay.
     * <p>
     * The delay is rounded down to whole server ticks; anything under one tick runs on the next one. Tasks queued
     * before the server has ticked once are discarded when the server starts, so this is for gameplay, not for start-up
     * work.
     */
    public static void schedule(Runnable runnable, Duration duration) {
        var delayInTicks = Math.max(0L, duration.toMillis() / MILLISECONDS_PER_TICK);

        SCHEDULED_TASKS.add(new ScheduledTask(currentTick + delayInTicks, runnable));
    }

    public static void tick(Level level) {
        if (level.isClientSide) {
            return;
        }

        var server = level.getServer();

        if (server == null || !server.isSameThread()) {
            return;
        }

        var serverTick = (long) server.getTickCount();

        if (server != owner) {
            owner = server;
            currentTick = serverTick;
            SCHEDULED_TASKS.clear();

            return;
        }

        // postLevelTick fires once per level; only the first call of a given tick should advance time.
        if (serverTick == currentTick) {
            return;
        }

        currentTick = serverTick;

        List<Runnable> dueTasks = null;

        for (var iterator = SCHEDULED_TASKS.iterator(); iterator.hasNext();) {
            var task = iterator.next();

            if (task.dueTick() > serverTick) {
                continue;
            }

            iterator.remove();

            if (dueTasks == null) {
                dueTasks = new ArrayList<>();
            }

            dueTasks.add(task.runnable());
        }

        if (dueTasks == null) {
            return;
        }

        for (var runnable : dueTasks) {
            runnable.run();
        }
    }

    private record ScheduledTask(
        long dueTick,
        Runnable runnable
    ) {}

    private ServerScheduler() {
        throw new UnsupportedOperationException();
    }
}
