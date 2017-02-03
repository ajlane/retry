package au.id.ajlane.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A completable version of a {@link RetryFuture}.
 *
 * @param <V>
 *     The type of the value returned by the task.
 */
class CompletableRetryFuture<V>
    extends CompletableFuture<V>
    implements RetryFuture<V>
{
    private final Runnable interrupt;
    private final Retry strategy;
    private volatile long delay;

    /**
     * Initialises the future.
     *
     * @param strategy
     *     The retry strategy. Must not be null.
     * @param interrupt
     *     A function which will interrupt the task if it is still running. Must not be null.
     */
    public CompletableRetryFuture(final Retry strategy, final Runnable interrupt)
    {
        this.strategy = strategy;
        this.interrupt = interrupt;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (mayInterruptIfRunning)
        {
            interrupt.run();
        }
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public int compareTo(Delayed o)
    {
        final long self = delay;
        final long other = o.getDelay(TimeUnit.MILLISECONDS);
        return self < other ? -1 : self == other ? 0 : 1;
    }

    @Override
    public long getDelay(TimeUnit unit)
    {
        return unit.convert(delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public Retry getStrategy()
    {
        return strategy;
    }

    /**
     * Sets the delay that will be reported by this future.
     *
     * @param delay
     *     The delay to report. Must be non-negative.
     * @param unit
     *     The units of the delay. Must not be null.
     */
    public void setDelay(final long delay, final TimeUnit unit)
    {
        this.delay = unit.toMillis(delay);
    }
}
