package graphql.execution.reactive;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Exercises the race between the last CompletableFuture completing (Actor 1)
 * and onComplete() being called (Actor 2) in {@link CompletionStageSubscriber}.
 * <p>
 * Before the fix, this race could cause onComplete to be stranded (never called
 * on the downstream subscriber), resulting in outcome (1, 0). After the fix,
 * the only acceptable outcome is (1, 1).
 */
@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "onNext and onComplete both delivered")
@Outcome(id = "1, 0", expect = Expect.FORBIDDEN, desc = "onComplete lost — race condition bug")
@State
public class CompletionStageSubscriber_onComplete_JCStress {

    private final CompletableFuture<String> future = new CompletableFuture<>();
    private final AtomicInteger onNextCount = new AtomicInteger();
    private final AtomicInteger onCompleteCount = new AtomicInteger();
    private final CompletionStageSubscriber<Integer, String> subscriber;

    public CompletionStageSubscriber_onComplete_JCStress() {
        Function<Integer, CompletionStage<String>> mapper = i -> future;

        Subscriber<String> downstream = new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription s) {
            }

            @Override
            public void onNext(String s) {
                onNextCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
                onCompleteCount.incrementAndGet();
            }
        };

        subscriber = new CompletionStageSubscriber<>(mapper, downstream);

        // Wire up subscription and enqueue one in-flight CF
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        subscriber.onNext(1);
    }

    @Actor
    public void actor1() {
        future.complete("value");
    }

    @Actor
    public void actor2() {
        subscriber.onComplete();
    }

    @Arbiter
    public void arbiter(II_Result r) {
        r.r1 = onNextCount.get();
        r.r2 = onCompleteCount.get();
    }
}
