package graphql.execution.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

import java.time.Duration;

/**
 * This uses the reactive streams TCK to test that our implementation meets spec
 */
public class SingleSubscriberPublisherTckVerificationTest extends PublisherVerification<String> {

    public SingleSubscriberPublisherTckVerificationTest() {
        super(new TestEnvironment(Duration.ofMillis(100).toMillis()));
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10000;
    }

    @Override
    public Publisher<String> createPublisher(long elements) {
        SingleSubscriberPublisher<String> publisher = new SingleSubscriberPublisher<>();
        for (int i = 0; i < elements; i++) {
            publisher.offer("n" + i);
        }
        publisher.noMoreData();
        return publisher;
    }

    @Override
    public Publisher<String> createFailedPublisher() {
        SingleSubscriberPublisher<String> publisher = new SingleSubscriberPublisher<>();
        publisher.offerError(new RuntimeException("Bang"));
        return publisher;
    }

}

