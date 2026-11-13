package graphql.execution.pubsub;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.example.unicast.AsyncIterablePublisher;

import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * This example publisher will create count "objects" and then terminate. Its
 * uses the reactive streams TCK as its implementation
 */
public class ReactiveStreamsObjectPublisher implements Publisher<Object> {

    private final AsyncIterablePublisher<Object> iterablePublisher;

    public ReactiveStreamsObjectPublisher(final int count, Function<Integer, Object> objectMaker) {
        Iterable<Object> iterable = mkIterable(count, objectMaker);
        iterablePublisher = new AsyncIterablePublisher<>(iterable, ForkJoinPool.commonPool());
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        iterablePublisher.subscribe(s);
    }

    private static Iterable<Object> mkIterable(int count, Function<Integer, Object> objectMaker) {
        return () -> new Iterator<Object>() {
            private int at = 0;

            @Override
            public boolean hasNext() {
                return at < count;
            }

            @Override
            public Object next() {
                Object message = objectMaker.apply(at);
                at++;
                return message;
            }
        };
    }

}
