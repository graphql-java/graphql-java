package graphql.execution.pubsub;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * This example publisher will create count "messages" and then terminate. It
 * uses the reactive streams TCK as its implementation
 */
public class ReactiveStreamsMessagePublisher extends CommonMessagePublisher implements Publisher<Message> {

    public ReactiveStreamsMessagePublisher(final int count) {
        super(count);
    }

    @Override
    public void subscribe(Subscriber<? super Message> s) {
        iterablePublisher.subscribe(s);
    }
}

