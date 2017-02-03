package au.id.ajlane.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;

/**
 * Represents the result of an asynchronous task.
 *
 * @param <V>
 *     The type of value returned by the task.
 */
public interface RetryFuture<V>
    extends ScheduledFuture<V>, CompletionStage<V>
{
    /**
     * The retry strategy that is being used.
     *
     * @return A retry strategy.
     */
    Retry getStrategy();
}
