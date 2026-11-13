package graphql.execution.pubsub;

import org.reactivestreams.FlowAdapters;

import java.util.concurrent.Flow;

/**
 * This example publisher will create count "messages" and then terminate. It
 * uses the reactive streams TCK as its implementation but presents itself
 * as a {@link Flow.Publisher}
 */
public class FlowMessagePublisher extends CommonMessagePublisher implements Flow.Publisher<Message> {

    public FlowMessagePublisher(int count) {
        super(count);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Message> s) {
        iterablePublisher.subscribe(FlowAdapters.toSubscriber(s));
    }
}
