package reproductions;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * Related to <a href="https://github.com/spring-projects/spring-graphql/issues/949">...</a>
 * <p>
 * This reproduction is to see what's happening with Subscriptions and whether they keep their
 * order when values are async.
 */
public class SubscriptionReproduction {
    public static void main(String[] args) {
        new SubscriptionReproduction().run(args);
    }

    private void run(String[] args) {

        boolean ordered = args.length > 0 && "ordered".equals(args[0]);

        GraphQL graphQL = mkGraphQl();
        String query = "subscription MySubscription {\n" +
                "  searchVideo {\n" +
                "    id\n" +
                "    name\n" +
                "    lastEpisode\n" +
                "    isFavorite\n" +
                "  }\n" +
                "}";

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).graphQLContext(
                b -> b.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, ordered)
        ).build();
        ExecutionResult executionResult = graphQL.execute(executionInput);
        Publisher<Map<String, Object>> publisher = executionResult.getData();

        DeathEater eater = new DeathEater();
        eater.eat(publisher);
    }

    private GraphQL mkGraphQl() {
        String sdl = "type Query { f : ID }" +
                "type Subscription {" +
                "   searchVideo : VideoSearch" +
                "}" +
                "type VideoSearch {" +
                "       id : ID" +
                "       name : String" +
                "       lastEpisode : String" +
                "       isFavorite : Boolean" +
                "}";
        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("Subscription")
                        .dataFetcher("searchVideo", this::mkFluxDF)
                )
                .type(newTypeWiring("VideoSearch")
                        .dataFetcher("name", this::nameDF)
                        .dataFetcher("isFavorite", this::isFavoriteDF)
                        .dataFetcher("lastEpisode", this::lastEpisode)
                )
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl), runtimeWiring
        );
        return GraphQL.newGraphQL(schema).build();
    }

    private CompletableFuture<Flux<Object>> mkFluxDF(DataFetchingEnvironment env) {
        // async deliver of the publisher with random snoozing between values
        Supplier<Flux<Object>> fluxSupplier = () -> Flux.generate(() -> 0, (counter, sink) -> {
            sink.next(mkValue(counter));
            snooze(rand(10, 100));
            if (counter == 10) {
                sink.complete();
            }
            return counter + 1;
        });
        return CompletableFuture.supplyAsync(fluxSupplier);
    }

    private Object isFavoriteDF(DataFetchingEnvironment env) {
        // async deliver of the isFavorite property with random delay
        return CompletableFuture.supplyAsync(() -> {
            Integer counter = getCounter(env.getSource());
            return counter % 2 == 0;
        });
    }

    private Object lastEpisode(DataFetchingEnvironment env) {
        // Mono-based async property that uses CF as the interface
        return Mono.fromCallable(() -> {
                    Integer counter = getCounter(env.getSource());
                    return "episode-" + Thread.currentThread().getName() + "for" + counter;
                })
                .publishOn(Schedulers.boundedElastic())
                .toFuture();
    }

    private Object nameDF(DataFetchingEnvironment env) {
        // async deliver of the isFavorite property with random delay
        return CompletableFuture.supplyAsync(() -> {
            Integer counter = getCounter(env.getSource());
            return "name" + counter;
        });
    }

    private static Integer getCounter(Map<String, Object> video) {
        Integer counter = (Integer) video.getOrDefault("counter", 0);
        snooze(rand(100, 500));
        return counter;
    }

    private @NotNull Object mkValue(Integer counter) {
        // name and isFavorite are future values via DFs
        return Map.of(
                "counter", counter,
                "id", String.valueOf(counter) // immediate value
        );
    }


    private static void snooze(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static Random rn = new Random();

    private static int rand(int min, int max) {
        return rn.nextInt(max - min + 1) + min;
    }

    public static class DeathEater implements Subscriber<Object> {
        private Subscription subscription;
        private final AtomicBoolean done = new AtomicBoolean();

        public boolean isDone() {
            return done.get();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            System.out.println("onSubscribe");
            subscription.request(10);
        }

        @Override
        public void onNext(Object o) {
            System.out.println("\tonNext : " + o);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("onError");
            throwable.printStackTrace(System.err);
            done.set(true);
        }

        @Override
        public void onComplete() {
            System.out.println("complete");
            done.set(true);
        }

        public void eat(Publisher<?> publisher) {
            publisher.subscribe(this);
            while (!this.isDone()) {
                snooze(2);
            }

        }
    }
}
