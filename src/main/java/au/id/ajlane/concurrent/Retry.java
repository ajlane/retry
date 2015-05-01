package au.id.ajlane.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public interface Retry
{
    public static Retry none() { return e -> -1; }

    public static Retry continuously() { return e -> 0; }

    default Retry limit(int n){
        final Retry self = this;
        return new Retry() {

            private volatile int attempts = 0;

            @Override
            public long getDelay(final Exception cause)
            {
                return attempts <= n ? self.getDelay(cause) : -1;
            }
        };
    }

    public static Retry every(final long period, final TimeUnit units) { return e -> units.toMillis(period); }

    public static Retry withLinearBackoff(final long period, final TimeUnit units)
    {
        return new Retry()
        {
            private volatile int attempts = 0;

            @Override
            public long getDelay(final Exception cause)
            {
                return units.toMillis(attempts * period);
            }
        };
    }

    public static Retry withExponentialBackoff(final long period, final TimeUnit units)
    {
        return new Retry()
        {
            private volatile int attempts = 0;

            @Override
            public long getDelay(final Exception cause)
            {
                if (attempts > 62)
                { return -1; }
                return units.toMillis((1 << attempts) * period);
            }
        };
    }

    long getDelay(final Exception cause);

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
        attempt.set(
            executorService.submit(
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

                                final long delay = Retry.this.getDelay(ex);
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
                }
            )
        );

        return result;
    }

}
