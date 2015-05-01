package au.id.ajlane.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

class CompletableRetryFuture<V>
    extends CompletableFuture<V>
    implements RetryFuture<V>
{
    private volatile long delay;
    private final Runnable interrupt;
    private final Retry strategy;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if(mayInterruptIfRunning){
            interrupt.run();
        }
        return super.cancel(mayInterruptIfRunning);
    }

    public CompletableRetryFuture(final Retry strategy, final Runnable interrupt)
    {
        this.strategy = strategy;
        this.interrupt = interrupt;
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

    public void setDelay(final long delay, final TimeUnit unit)
    {
        this.delay = unit.toMillis(delay);
    }
}
