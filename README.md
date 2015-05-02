# Retry
An utility for automatically retrying failed tasks according to a retry strategy. Built-in strategies include linear and exponential back-off.

If a task fails by throwing an exception, the exception will be supressed while the task is re-executed. If no more retries are permitted _all_ of the suppressed exceptions will be available via the task's `Future`.

Tasks can be executed asynchronously by providing a `ScheduledExecutionService`.

Retry is provided under the [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Example
Attempts to search twitter for tweets matching a given query. If a `TwitterException` is thrown, the task will be retried after 5, 10, 20, 40, and 80 seconds, using the exponential back-off strategy.

```java
Future<List<Status>> result =
    Retry.withExponentialBackoff( 5, TimeUnit.SECONDS )
         .when( TwitterException.class )
         .limit( 5 )
         .execute(() ->
            twitter.search( query )
                   .getTweets()
         );
```