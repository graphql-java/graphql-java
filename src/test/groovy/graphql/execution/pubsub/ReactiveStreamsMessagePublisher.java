package graphql.execution.pubsub;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.example.unicast.AsyncIterablePublisher;

import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * This example publisher will create count "messages" and then terminate. Its
 * uses the reactive streams TCK as its implementation
 */
public class ReactiveStreamsMessagePublisher implements Publisher<Message> {

    private final AsyncIterablePublisher<Message> iterablePublisher;

    public ReactiveStreamsMessagePublisher(final int count) {
        Iterable<Message> iterable = mkIterable(count, at -> {
            Message message = new Message("sender" + at, "text" + at);
            return examineMessage(message, at);
        });
        iterablePublisher = new AsyncIterablePublisher<>(iterable, ForkJoinPool.commonPool());
    }

    @Override
    public void subscribe(Subscriber<? super Message> s) {
        iterablePublisher.subscribe(s);
    }

    @SuppressWarnings("unused")
    protected Message examineMessage(Message message, Integer at) {
        return message;
    }

    private static Iterable<Message> mkIterable(int count, Function<Integer, Message> msgMaker) {
        return () -> new Iterator<Message>() {
            private int at = 0;

            @Override
            public boolean hasNext() {
                return at < count;
            }

            @Override
            public Message next() {
                Message message = msgMaker.apply(at);
                at++;
                return message;
            }
        };
    }

}
