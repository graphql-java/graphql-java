package graphql.execution.pubsub;

import org.reactivestreams.example.unicast.AsyncIterablePublisher;

import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

class CommonMessagePublisher {

    protected final AsyncIterablePublisher<Message> iterablePublisher;

    protected CommonMessagePublisher(final int count) {
        Iterable<Message> iterable = mkIterable(count, at -> {
            Message message = new Message("sender" + at, "text" + at);
            return examineMessage(message, at);
        });
        iterablePublisher = new AsyncIterablePublisher<>(iterable, ForkJoinPool.commonPool());
    }

    @SuppressWarnings("unused")
    protected Message examineMessage(Message message, Integer at) {
        return message;
    }

    private static Iterable<Message> mkIterable(int count, Function<Integer, Message> msgMaker) {
        return () -> new Iterator<>() {
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
