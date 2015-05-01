package au.id.ajlane.concurrent;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryStrategyTest
{
    @Test
    public void testNoneSuccess()
    {
        final AtomicInteger attempts = new AtomicInteger(0);
        Retry.none()
             .execute(attempts::incrementAndGet);
        Assert.assertEquals(1, attempts.get());
    }

    @Test
    public void testNoneFailure()
    {
        final AtomicInteger attempts = new AtomicInteger(0);
        Retry.none()
             .execute(
                 () -> {
                     int n = attempts.getAndIncrement();
                     if (n < 1)
                     {
                         throw new RuntimeException();
                     }
                     return n;
                 }
             );
        Assert.assertEquals(1, attempts.get());
    }

    @Test
    public void testContinuously()
    {
        final AtomicInteger attempts = new AtomicInteger(0);
        Retry.continuously()
             .limit(10)
             .execute(
                 () -> {
                     int n = attempts.getAndIncrement();
                     if (n < 4)
                     {
                         throw new RuntimeException();
                     }
                     return n;
                 }
             );
        Assert.assertEquals(5, attempts.get());
    }

    @Test
    public void testExponentialBackoffSuccess()
    {
        final AtomicInteger attempts = new AtomicInteger(0);
        Retry.withExponentialBackoff(1, TimeUnit.MILLISECONDS)
             .execute(attempts::incrementAndGet);
        Assert.assertEquals(1, attempts.get());
    }
}
