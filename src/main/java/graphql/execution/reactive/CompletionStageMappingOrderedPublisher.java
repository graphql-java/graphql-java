package graphql.execution.reactive;

import graphql.Internal;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A reactive Publisher that bridges over another Publisher of `D` and maps the results
 * to type `U` via a CompletionStage, handling errors in that stage but keeps the results
 * in order of downstream publishing. This means it must queue unfinished
 * completion stages in memory in arrival order.
 *
 * @param <D> the downstream type
 * @param <U> the upstream type to be mapped to
 */
@Internal
public class CompletionStageMappingOrderedPublisher<D, U> extends CompletionStageMappingPublisher<D, U> {
    /**
     * You need the following :
     *
     * @param upstreamPublisher an upstream source of data
     * @param mapper            a mapper function that turns upstream data into a promise of mapped D downstream data
     */
    public CompletionStageMappingOrderedPublisher(Publisher<U> upstreamPublisher, Function<U, CompletionStage<D>> mapper) {
        super(upstreamPublisher, mapper);
    }

    @Override
    protected @NotNull Subscriber<? super U> createSubscriber(Subscriber<? super D> downstreamSubscriber) {
        return new CompletionStageOrderedSubscriber<>(mapper, downstreamSubscriber);
    }
}
