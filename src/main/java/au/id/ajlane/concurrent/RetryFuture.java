package au.id.ajlane.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;

public interface RetryFuture<V>
    extends ScheduledFuture<V>, CompletionStage<V>
{
    public Retry getStrategy();
}
