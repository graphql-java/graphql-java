package graphql.execution.pubsub;

import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This example publisher will create count "messages" and then terminate. Its
 * uses tRxJava Flowable as a backing publisher
 */
public class RxJavaMessagePublisher implements Publisher<Message> {

    private final Flowable<Message> flowable;
    private final AtomicInteger counter = new AtomicInteger();

    public RxJavaMessagePublisher(final int count) {
        flowable = Flowable.range(0, count)
                .map(at -> {
                    counter.incrementAndGet();
                    return examineMessage(new Message("sender" + at, "text" + at), at);
                });
    }

    public int getCounter() {
        return counter.get();
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
