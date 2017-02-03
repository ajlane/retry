package au.id.ajlane.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An executor which doesn't use any other threads - executing all tasks in the same thread that requested them.
 */
public class SameThreadExecutorService
    implements ScheduledExecutorService
{
    private static class ImmediateCancelledFuture<V>
        implements Future<V>, ScheduledFuture<V>
    {
        ImmediateCancelledFuture()
        {
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        @Override
        public int compareTo(Delayed o)
        {
            return o.getDelay(TimeUnit.MILLISECONDS) > 0 ? -1 : 0;
        }

        @Override
        public V get()
            throws InterruptedException, ExecutionException
        {
            throw new CancellationException();
        }

        @Override
        public V get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
        {
            throw new CancellationException();
        }

        @Override
        public long getDelay(TimeUnit unit)
        {
            return 0;
        }

        @Override
        public boolean isCancelled()
        {
            return true;
        }

        @Override
        public boolean isDone()
        {
            return true;
        }
    }

    private static class ImmediateFailureFuture<V>
        implements Future<V>, ScheduledFuture<V>
    {
        private final Throwable cause;

        ImmediateFailureFuture(final Throwable cause)
        {
            this.cause = cause;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        @Override
        public int compareTo(Delayed o)
        {
            return o.getDelay(TimeUnit.MILLISECONDS) > 0 ? -1 : 0;
        }

        @Override
        public V get()
            throws InterruptedException, ExecutionException
        {
            throw new ExecutionException(cause);
        }

        @Override
        public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
        {
            throw new ExecutionException(cause);
        }

        @Override
        public long getDelay(TimeUnit unit)
        {
            return 0;
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return true;
        }
    }

    private static class ImmediateSuccessFuture<V>
        implements Future<V>, ScheduledFuture<V>
    {
        private final V value;

        ImmediateSuccessFuture(final V value)
        {
            this.value = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        @Override
        public int compareTo(Delayed o)
        {
            return o.getDelay(TimeUnit.MILLISECONDS) > 0 ? -1 : 0;
        }

        @Override
        public V get()
            throws InterruptedException, ExecutionException
        {
            return value;
        }

        @Override
        public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
        {
            return value;
        }

        @Override
        public long getDelay(TimeUnit unit)
        {
            return 0;
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return true;
        }
    }

    private static final SameThreadExecutorService INSTANCE = new SameThreadExecutorService();

    /**
     * Gets an executor which will execute all tasks in the calling thread.
     *
     * @return An executor.
     */
    public static ScheduledExecutorService get()
    {
        return INSTANCE;
    }

    private SameThreadExecutorService()
    {
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit)
        throws InterruptedException
    {
        return false;
    }

    @Override
    public void execute(final Runnable command)
    {
        command.run();
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks)
        throws InterruptedException
    {
        final List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks)
        {
            if (Thread.interrupted())
            {
                throw new InterruptedException();
            }
            futures.add(submit(task));
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        final Collection<? extends Callable<T>> tasks,
        final long timeout,
        final TimeUnit unit
    )
        throws InterruptedException
    {
        long limit = System.currentTimeMillis() + unit.toMillis(timeout);
        final List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks)
        {
            if (Thread.interrupted())
            {
                throw new InterruptedException();
            }
            if (System.currentTimeMillis() < limit)
            {
                futures.add(submit(task));
            }
            else
            {
                futures.add(new ImmediateCancelledFuture<>());
            }
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException
    {
        final ArrayDeque<Throwable> failures = new ArrayDeque<>(tasks.size());
        for (Callable<T> task : tasks)
        {
            if (Thread.interrupted())
            {
                throw new InterruptedException();
            }
            final Future<T> result = submit(task);
            if (result.isCancelled())
            {
                throw new InterruptedException();
            }
            try
            {
                return result.get();
            }
            catch (final ExecutionException ex)
            {
                failures.push(ex.getCause());
            }
        }
        final Throwable cause = failures.pop();
        while (!failures.isEmpty())
        {
            cause.addSuppressed(failures.pop());
        }
        throw new ExecutionException(cause);

    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {

        long limit = System.currentTimeMillis() + unit.toMillis(timeout);
        final ArrayDeque<Throwable> failures = new ArrayDeque<>(tasks.size());
        for (Callable<T> task : tasks)
        {
            if (Thread.interrupted())
            {
                throw new InterruptedException();
            }
            if (limit < System.currentTimeMillis())
            {
                throw new TimeoutException();
            }
            final Future<T> result = submit(task);
            if (result.isCancelled())
            {
                throw new InterruptedException();
            }
            try
            {
                return result.get();
            }
            catch (final ExecutionException ex)
            {
                failures.push(ex.getCause());
            }
        }
        final Throwable cause = failures.pop();
        while (!failures.isEmpty())
        {
            cause.addSuppressed(failures.pop());
        }
        throw new ExecutionException(cause);
    }

    @Override
    public boolean isShutdown()
    {
        return false;
    }

    @Override
    public boolean isTerminated()
    {
        return false;
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit)
    {
        try
        {
            Thread.sleep(unit.toMillis(delay));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread()
                .interrupt();
            return new ImmediateCancelledFuture<Void>();
        }
        try
        {
            command.run();
            return new ImmediateSuccessFuture<Void>(null);
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<Void>(ex);
        }
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit)
    {
        try
        {
            Thread.sleep(unit.toMillis(delay));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread()
                .interrupt();
            return new ImmediateCancelledFuture<>();
        }
        try
        {
            return new ImmediateSuccessFuture<>(callable.call());
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        final Runnable command,
        final long initialDelay,
        final long period,
        final TimeUnit unit
    )
    {
        long next = System.currentTimeMillis() + unit.toMillis(initialDelay);
        try
        {
            Thread.sleep(Math.max(0, System.currentTimeMillis() - next));
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread()
                .interrupt();
            return new ImmediateCancelledFuture<>();
        }
        try
        {
            command.run();
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }
        while (!Thread.interrupted())
        {
            next += unit.toMillis(period);
            try
            {
                Thread.sleep(Math.max(0, System.currentTimeMillis() - next));
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread()
                    .interrupt();
                return new ImmediateCancelledFuture<>();
            }
            try
            {
                command.run();
            }
            catch (Throwable ex)
            {
                return new ImmediateFailureFuture<>(ex);
            }
        }
        Thread.currentThread()
            .interrupt();
        return new ImmediateCancelledFuture<>();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        final Runnable command,
        final long initialDelay,
        final long delay,
        final TimeUnit unit
    )
    {
        try
        {
            Thread.sleep(unit.toMillis(initialDelay));
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread()
                .interrupt();
            return new ImmediateCancelledFuture<>();
        }
        try
        {
            command.run();
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }
        while (!Thread.interrupted())
        {
            try
            {
                Thread.sleep(unit.toMillis(delay));
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread()
                    .interrupt();
                return new ImmediateCancelledFuture<>();
            }
            try
            {
                command.run();
            }
            catch (Throwable ex)
            {
                return new ImmediateFailureFuture<>(ex);
            }
        }
        Thread.currentThread()
            .interrupt();
        return new ImmediateCancelledFuture<>();
    }

    @Override
    public void shutdown()
    {
        throw new UnsupportedOperationException(
            "The same-thread executor cannot be shutdown, because it does not control any of its threads."
        );
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task)
    {
        try
        {
            return new ImmediateSuccessFuture<>(task.call());
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }

    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result)
    {
        try
        {
            task.run();
            return new ImmediateSuccessFuture<>(result);
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }

    }

    @Override
    public Future<?> submit(final Runnable task)
    {
        try
        {
            task.run();
            return new ImmediateSuccessFuture<Void>(null);
        }
        catch (Throwable ex)
        {
            return new ImmediateFailureFuture<>(ex);
        }
    }

}
