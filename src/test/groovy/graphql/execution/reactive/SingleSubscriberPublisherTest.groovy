package graphql.execution.reactive

import org.awaitility.Awaitility
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class SingleSubscriberPublisherTest extends Specification {

    class AppendingSubscriber implements Subscriber<String> {
        Subscription subscription
        String result = ""
        AtomicBoolean done = new AtomicBoolean()

        @Override
        void onSubscribe(Subscription s) {
            subscription = s
            s.request(Integer.MAX_VALUE)
        }

        @Override
        void onNext(String s) {
            result = result + s
        }

        @Override
        void onError(Throwable t) {
            assertState(t)
            done.set(true)
        }

        @Override
        void onComplete() {
            assertState(null)
            done.set(true)
        }

        void assertState(Throwable t) {
        }

        boolean await() {
            Awaitility.await().untilTrue(done)
            return true
        }

    }

    def "basic data is offered out"() {
        given:
        SingleSubscriberPublisher<String> publisher = new SingleSubscriberPublisher<>()
        when:
        publisher.offer("Mary")
        publisher.offer("Had")
        publisher.offer("A")
        publisher.offer("Little")
        publisher.offer("Lamb")
        publisher.noMoreData()

        def subscriber = new AppendingSubscriber() {
            @Override
            void assertState(Throwable t) {
                assert t == null
                assert result == "MaryHadALittleLamb"
            }
        }
        publisher.subscribe(subscriber)

        then:
        subscriber.await()
    }
}
