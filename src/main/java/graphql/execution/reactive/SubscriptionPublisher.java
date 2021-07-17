package graphql.execution.reactive;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.PublicApi;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;


/**
 * Subscription queries return an instance of this class in the {@link ExecutionResult} data element
 * for the subscribed field.
 *
 * <pre>{@code
 *      ExecutionResult er = graphQL.execute("subscription s { onEntityChanged(id : "1") { selection1, selection2 }}")
 *      SubscriptionPublisher eventPublisher = er.getData("onEntityChanged")
 * }
 * </pre>
 */
@PublicApi
public class SubscriptionPublisher extends CompletionStageMappingPublisher<ExecutionResult, Object> {

    /**
     * Subscription consuming code is not expected to create instances of this class
     *
     * @param upstreamPublisher the original publisher of objects that then have a graphql selection set applied to them
     * @param mapper            a mapper that turns object into promises to execution results which are then published on this stream
     */
    @Internal
    public SubscriptionPublisher(Publisher<Object> upstreamPublisher, Function<Object, CompletionStage<ExecutionResult>> mapper) {
        super(upstreamPublisher, mapper);
    }

    /**
     * @return the underlying Publisher that was providing raw objects to the subscription field, whose published values are then mapped
     * to execution results
     */
    @Override
    public Publisher<Object> getUpstreamPublisher() {
        return super.getUpstreamPublisher();
    }
}
