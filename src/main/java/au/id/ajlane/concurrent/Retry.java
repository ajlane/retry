package au.id.ajlane.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public interface Retry
{
    public static Retry continuously()
    {
        return (attempts, cause) -> 0;
    }

    public static Retry every(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> units.toMillis(period);
    }

    public static Retry none()
    {
        return (attempts, cause) -> -1;
    }

    public static Retry withExponentialBackoff(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> attempts > 62 ? -1 : units.toMillis((1 << attempts) * period);
    }

    public static Retry withLinearBackoff(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> units.toMillis(attempts * period);
    }

    default <V> RetryFuture<V> execute(final Callable<V> task)
    {
        return execute(task, SameThreadExecutorService.get());
    }

    default <V> RetryFuture<V> execute(final Callable<V> task, final ScheduledExecutorService executorService)
    {
        final AtomicReference<Future<?>> attempt = new AtomicReference<>();
        final CompletableRetryFuture<V> result = new CompletableRetryFuture<>(
            this,
            () -> attempt.get()
                         .cancel(true)
        );
        final AtomicReference<Exception> suppressed = new AtomicReference<>();
        final AtomicInteger attempts = new AtomicInteger(0);
        final long initialDelay = getDelay(0, null);
        attempt.set(
            executorService.schedule(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (!result.isCancelled())
                        {
                            try
                            {
                                result.complete(task.call());
                            } catch (final Exception ex)
                            {
                                final Exception previouslySuppressed = suppressed.get();
                                if (previouslySuppressed != null)
                                {
                                    ex.addSuppressed(previouslySuppressed);
                                }

                                final long delay = Retry.this.getDelay(attempts.incrementAndGet(), ex);
                                if (delay >= 0)
                                {
                                    suppressed.set(ex);
                                    attempt.set(executorService.schedule(this, delay, TimeUnit.MILLISECONDS));
                                }
                                else
                                {
                                    result.completeExceptionally(ex);
                                }
                            }
                        }
                    }
                }, initialDelay, TimeUnit.MILLISECONDS
            )
        );

        return result;
    }

    long getDelay(final int attempts, final Exception cause);

    default Retry limit(int n)
    {
        return (attempts, cause) -> attempts <= n ? this.getDelay(attempts, cause) : -1;
    }

    default Retry when(final Predicate<? super Exception> predicate)
    {
        return (attempts, cause) -> predicate.test(cause) ? this.getDelay(attempts, cause) : -1;
    }

    default Retry when(final Class<? extends Exception> type)
    {
        return when(type::isInstance);
    }
}
