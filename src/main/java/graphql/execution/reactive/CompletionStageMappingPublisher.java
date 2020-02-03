package graphql.execution.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A reactive Publisher that bridges over another Publisher of `D` and maps the results
 * to type `U` via a CompletionStage, handling errors in that stage
 *
 * @param <D> the down stream type
 * @param <U> the up stream type to be mapped to
 */
public class CompletionStageMappingPublisher<D, U> implements Publisher<D> {
    private final Publisher<U> upstreamPublisher;
    private final Function<U, CompletionStage<D>> mapper;

    /**
     * You need the following :
     *
     * @param upstreamPublisher an upstream source of data
     * @param mapper            a mapper function that turns upstream data into a promise of mapped D downstream data
     */
    public CompletionStageMappingPublisher(Publisher<U> upstreamPublisher, Function<U, CompletionStage<D>> mapper) {
        this.upstreamPublisher = upstreamPublisher;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Subscriber<? super D> downstreamSubscriber) {
        upstreamPublisher.subscribe(new Subscriber<U>() {
            Subscription delegatingSubscription;
            private AtomicInteger inFlight;
            private volatile Runnable finish;

            @Override
            public void onSubscribe(Subscription subscription) {
                inFlight = new AtomicInteger();
                delegatingSubscription = new DelegatingSubscription(subscription);
                downstreamSubscriber.onSubscribe(delegatingSubscription);
            }

            @Override
            public void onNext(U u) {
                CompletionStage<D> completionStage;
                try {
                    completionStage = mapper.apply(u);
                    inFlight.getAndIncrement();
                    completionStage.whenComplete((d, throwable) -> {
                        try {
                            if (throwable != null) {
                                handleThrowable(throwable);
                            } else {
                                downstreamSubscriber.onNext(d);
                            }
                        }finally {
                            if(inFlight.intValue() == 1 && finish != null) {
                                finish.run();
                                finish = null;
                            }
                            inFlight.decrementAndGet();
                        }
                    });
                } catch (RuntimeException throwable) {
                    handleThrowable(throwable);
                }
            }

            private void handleThrowable(Throwable throwable) {
                downstreamSubscriber.onError(throwable);
                //
                // reactive semantics say that IF an exception happens on a publisher
                // then onError is called and no more messages flow.  But since the exception happened
                // during the mapping, the upstream publisher does not no about this.
                // so we cancel to bring the semantics back together, that is as soon as an exception
                // has happened, no more messages flow
                //
                delegatingSubscription.cancel();
            }

            @Override
            public void onError(Throwable t) {
                if(inFlight.intValue() > 0) {
                    finish = () -> downstreamSubscriber.onError(t);
                    if(inFlight.intValue() == 0 && finish != null) {
                        //happened together
                        downstreamSubscriber.onError(t);
                    }
                }else {
                    downstreamSubscriber.onError(t);
                }
            }

            @Override
            public void onComplete() {
                if(inFlight.intValue() > 0) {
                    finish = () -> downstreamSubscriber.onComplete();
                    if(inFlight.intValue() == 0 && finish != null) {
                        //happened together
                        downstreamSubscriber.onComplete();
                    }
                }else {
                    downstreamSubscriber.onComplete();
                }
            }
        });
    }
}
