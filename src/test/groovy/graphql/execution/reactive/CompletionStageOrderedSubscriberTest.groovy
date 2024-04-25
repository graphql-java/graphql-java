package graphql.execution.reactive

import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.CapturingSubscription
import org.reactivestreams.Subscriber

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Function

class CompletionStageOrderedSubscriberTest extends CompletionStageSubscriberTest {

    @Override
    protected Subscriber<Integer> createSubscriber(Function<Integer, CompletionStage<String>> mapper, CapturingSubscriber<Object> capturingSubscriber) {
        return new CompletionStageOrderedSubscriber<Integer, String>(mapper, capturingSubscriber)
    }

    @Override
    protected ArrayList<String> expectedOrderingOfAsyncCompletion() {
        return ["0", "1", "2", "3"]
    }
}
