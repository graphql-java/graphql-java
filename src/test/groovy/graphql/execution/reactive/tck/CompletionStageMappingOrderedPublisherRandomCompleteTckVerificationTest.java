package graphql.execution.reactive.tck;

import graphql.execution.reactive.CompletionStageMappingOrderedPublisher;
import graphql.execution.reactive.CompletionStageMappingPublisher;
import io.reactivex.Flowable;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * This uses the reactive streams TCK to test that our CompletionStageMappingPublisher meets spec
 * when it's got CFs that complete at different times
 */
@Test
public class CompletionStageMappingOrderedPublisherRandomCompleteTckVerificationTest extends PublisherVerification<String> {

    public CompletionStageMappingOrderedPublisherRandomCompleteTckVerificationTest() {
        super(new TestEnvironment(Duration.ofMillis(100).toMillis()));
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10000;
    }

    @Override
    public Publisher<String> createPublisher(long elements) {
        Publisher<Integer> publisher = Flowable.range(0, (int) elements);
        Function<Integer, CompletionStage<String>> mapper = mapperFunc();
        return new CompletionStageMappingOrderedPublisher<>(publisher, mapper);
    }
    @Override
    public Publisher<String> createFailedPublisher() {
        Publisher<Integer> publisher = Flowable.error(() -> new RuntimeException("Bang"));
        Function<Integer, CompletionStage<String>> mapper = mapperFunc();
        return new CompletionStageMappingOrderedPublisher<>(publisher, mapper);
    }

    public boolean skipStochasticTests() {
        return true;
    }

    @NotNull
    private static Function<Integer, CompletionStage<String>> mapperFunc() {
        return i -> CompletableFuture.supplyAsync(() -> {
            int ms = rand(0, 5);
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return i + "!";
        });
    }

    static Random rn = new Random();

    private static int rand(int min, int max) {
        return rn.nextInt(max - min + 1) + min;
    }

}

