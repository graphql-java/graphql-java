package graphql.execution.reactive.tck;

import graphql.execution.reactive.CompletionStageMappingOrderedPublisher;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * This uses the reactive streams TCK to test that our CompletionStageMappingPublisher meets spec
 * when it's got CFs that complete off thread.
 * <p>
 * Uses a shared single-thread executor per publisher so CFs complete sequentially.
 * The ordered subscriber drains completed CFs in a while loop — with concurrent executors,
 * multiple CFs can complete before the drain starts, causing multiple onNext calls on the
 * same thread which the TCK flags as a spec303 (unbounded recursion) violation.
 */
@Test
public class CompletionStageMappingOrderedPublisherTckVerificationTest extends PublisherVerification<String> {

    public CompletionStageMappingOrderedPublisherTckVerificationTest() {
        super(new TestEnvironment(Duration.ofMillis(1000).toMillis()));
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10000;
    }

    @Override
    public Publisher<String> createPublisher(long elements) {
        Publisher<Integer> publisher = Flowable.range(0, (int) elements);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Function<Integer, CompletionStage<String>> mapper = i -> CompletableFuture.supplyAsync(() -> i + "!", executor);
        return new CompletionStageMappingOrderedPublisher<>(publisher, mapper);
    }

    @Override
    public Publisher<String> createFailedPublisher() {
        Publisher<Integer> publisher = Flowable.error(() -> new RuntimeException("Bang"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Function<Integer, CompletionStage<String>> mapper = i -> CompletableFuture.supplyAsync(() -> i + "!", executor);
        return new CompletionStageMappingOrderedPublisher<>(publisher, mapper);
    }

    @Override
    public boolean skipStochasticTests() {
        return true;
    }
}

