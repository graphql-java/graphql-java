package graphql.execution.pubsub;

import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * This example publisher will create count "messages" and then terminate. Its
 * uses tRxJava Flowable as a backing publisher
 */
public class RxJavaMessagePublisher implements Publisher<Message> {

    private final Flowable<Message> flowable;

    public RxJavaMessagePublisher(final int count) {
        flowable = Flowable.range(0, count)
                .map(at -> examineMessage(new Message("sender" + at, "text" + at), at));
    }

    @Override
    public void subscribe(Subscriber<? super Message> s) {
        flowable.subscribe(s);
    }

    @SuppressWarnings("unused")
    protected Message examineMessage(Message message, Integer at) {
        return message;
    }
}
