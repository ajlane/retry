package au.id.ajlane.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * A utility for retrying tasks when they fail.
 */
public interface Retry
{
    /**
     * A utility which will continuously retry the failed task, without any delay or limit.
     *
     * @return A ready utility.
     */
    static Retry continuously()
    {
        return (attempts, cause) -> 0;
    }

    /**
     * A utility which will retry the failed task periodically.
     *
     * @param period
     *     The length of the period to wait between attempts. Must be non-negative.
     * @param units
     *     The units of the length of the period. Must not be null.
     *
     * @return A ready utility.
     */
    static Retry every(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> units.toMillis(period);
    }

    /**
     * A utility which does not actually retry at all.
     *
     * @return A ready utility.
     */
    static Retry none()
    {
        return (attempts, cause) -> attempts == 0 ? 0 : -1;
    }

    /**
     * A utility which will retry the failed task with exponentially increasing time intervals.
     * <p>
     * After each attempt, the delay between tasks will double.
     *
     * @param period
     *     The length of the first delay. Must be non-negative.
     * @param units
     *     The units of the first delay. Must not be null.
     *
     * @return A ready utility.
     */
    static Retry withExponentialBackoff(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> attempts > 62 ? -1 : units.toMillis((1 << attempts) * period);
    }

    /**
     * A utility which will retry the failed task with linearly increasing time intervals.
     * <p>
     * After each attempt, the delay will be increased by one extra period.
     *
     * @param period
     *     The initial period of delay. Must be non-negative.
     * @param units
     *     The units of the period of dleay. Must not be null.
     *
     * @return A ready utility.
     */
    static Retry withLinearBackoff(final long period, final TimeUnit units)
    {
        return (attempts, cause) -> units.toMillis(attempts * period);
    }

    /**
     * Executes the given task, retrying if it fails according to the utility's retry strategy.
     *
     * @param task
     *     The task to execute. Must not be null.
     * @param <V>
     *     The type of value returned by the task.
     *
     * @return A future which will provide the final result of the task.
     */
    default <V> RetryFuture<V> execute(final Callable<V> task)
    {
        return execute(task, SameThreadExecutorService.get());
    }

    /**
     * Executes the given task, using the given executor, retrying if it fails according to the utility's retry
     * strategy.
     *
     * @param task
     *     The task to execute. Must not be null.
     * @param executorService
     *     An executor to use to schedule the task and its retries. Must not be null.
     * @param <V>
     *     The type of value returned by the task.
     *
     * @return A future which will provide the final result of the task.
     */
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
                            }
                            catch (final Exception ex)
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
                }, initialDelay > 0 ? initialDelay : 0, TimeUnit.MILLISECONDS
            )
        );

        return result;
    }

    /**
     * Calculates the necessary delay between the current and next attempt, given the current state.
     *
     * @param attempts
     *     The number of attempts so far. Must be non-negative.
     * @param cause
     *     The cause of the failure, if the task failed. May be null if the task has not run yet.
     *
     * @return A delay, in milliseconds. If the delay is negative, no more attempts should be made.
     */
    long getDelay(final int attempts, final Exception cause);

    /**
     * Extends the existing retry policy to limit the number of attempts.
     *
     * @param n
     *     The maximum number of attempts. Must be non-negative.
     *
     * @return A utility with the new policy.
     */
    default Retry limit(int n)
    {
        return (attempts, cause) -> attempts <= n ? this.getDelay(attempts, cause) : -1;
    }

    /**
     * Extends the existing retry policy to only retry if the cause of failure meets some criteria.
     *
     * @param predicate
     *     A function to test whether a retry attempt should be made. The function should return {@code true} to rery,
     *     or {@code false} to give up.
     *
     * @return A utility with the new policy.
     */
    default Retry when(final Predicate<? super Exception> predicate)
    {
        return (attempts, cause) -> predicate.test(cause) ? this.getDelay(attempts, cause) : -1;
    }

    /**
     * Extends the existing retry policy to only retry if the cause of failure matches a particular type.
     *
     * @param type
     *     The type of failure that should cause a retry. Must not be null.
     *
     * @return A utility with the new policy.
     */
    default Retry when(final Class<? extends Exception> type)
    {
        return when(type::isInstance);
    }
}
