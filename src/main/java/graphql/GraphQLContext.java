package graphql;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;


/**
 * This context object can be used to contain key values that can be useful as "context" when executing
 * {@link graphql.schema.DataFetcher}s
 *
 * <pre>
 * {@code
 *     DataFetcher df = new DataFetcher() {
 *        public Object get(DataFetchingEnvironment env) {
 *            GraphQLContext ctx = env.getGraphqlContext()
 *            User currentUser = ctx.getOrDefault("userKey",new AnonymousUser())
 *            ...
 *        }
 *     }
 * }
 * </pre>
 *
 * You can set this up via {@link ExecutionInput#getGraphQLContext()}
 *
 * All keys and values in the context MUST be non null.
 *
 * The class is mutable via a thread safe implementation but it is recommended to try to use this class in an immutable way if you can.
 */
@PublicApi
@ThreadSafe
@SuppressWarnings("unchecked")
public class GraphQLContext {

    private final ConcurrentMap<Object, Object> map;

    private GraphQLContext(ConcurrentMap<Object, Object> map) {
        this.map = map;
    }

    /**
     * Deletes a key in the context
     *
     * @param key the key to delete
     *
     * @return this GraphQLContext object
     */
    public GraphQLContext delete(Object key) {
        map.remove(assertNotNull(key));
        return this;
    }

    /**
     * Returns a value in the context by key
     *
     * @param key the key to look up
     * @param <T> for two
     *
     * @return a value or null
     */
    public <T> T get(Object key) {
        return (T) map.get(assertNotNull(key));
    }

    /**
     * Returns a value in the context by key
     *
     * @param key          the key to look up
     * @param defaultValue the default value to use if these is no key entry
     * @param <T>          for two
     *
     * @return a value or default value
     */
    public <T> T getOrDefault(Object key, T defaultValue) {
        return (T) map.getOrDefault(assertNotNull(key), defaultValue);
    }

    /**
     * Returns a {@link Optional} value in the context by key
     *
     * @param key the key to look up
     * @param <T> for two
     *
     * @return a value or an empty optional value
     */
    public <T> Optional<T> getOrEmpty(Object key) {
        T t = (T) map.get(assertNotNull(key));
        return Optional.ofNullable(t);
    }

    /**
     * Returns true if the context contains a value for that key
     *
     * @param key the key to lookup
     *
     * @return true if there is a value for that key
     */
    public boolean hasKey(Object key) {
        return map.containsKey(assertNotNull(key));
    }

    /**
     * Puts a value into the context
     *
     * @param key   the key to set
     * @param value the new value (which not must be null)
     *
     * @return this {@link GraphQLContext} object
     */
    public GraphQLContext put(Object key, Object value) {
        map.put(assertNotNull(key), assertNotNull(value));
        return this;
    }

    /**
     * Puts all of the values into the context
     *
     * @param map the map of values to use
     *
     * @return this {@link GraphQLContext} object
     */
    public GraphQLContext putAll(Map<?, Object> map) {
        assertNotNull(map);
        for (Map.Entry<?, Object> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Puts all of the values into the context
     *
     * @param context the other context to use
     *
     * @return this {@link GraphQLContext} object
     */
    public GraphQLContext putAll(GraphQLContext context) {
        assertNotNull(context);
        return putAll(context.map);
    }

    /**
     * Puts all of the values into the context
     *
     * @param contextBuilder the other context to use
     *
     * @return this {@link GraphQLContext} object
     */
    public GraphQLContext putAll(GraphQLContext.Builder contextBuilder) {
        assertNotNull(contextBuilder);
        return putAll(contextBuilder.build());
    }

    /**
     * Puts all of the values into the context
     *
     * @param contextBuilderConsumer a call back to that gives out a builder to use
     *
     * @return this {@link GraphQLContext} object
     */
    public GraphQLContext putAll(Consumer<GraphQLContext.Builder> contextBuilderConsumer) {
        assertNotNull(contextBuilderConsumer);
        Builder builder = newContext();
        contextBuilderConsumer.accept(builder);
        return putAll(builder);
    }

    /**
     * @return a stream of entries in the context
     */
    public Stream<Map.Entry<Object, Object>> stream() {
        return map.entrySet().stream();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GraphQLContext that = (GraphQLContext) o;
        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }

    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * Creates a new GraphqlContext with the map of context added to it
     *
     * @param mapOfContext the map of context value to use
     *
     * @return the new GraphqlContext
     */
    public static GraphQLContext of(Map<?, Object> mapOfContext) {
        return new Builder().of(mapOfContext).build();
    }

    /**
     * Creates a new GraphqlContext with the map of context added to it
     *
     * @param contextBuilderConsumer a callback that is given a new builder
     *
     * @return the new GraphqlContext
     */
    public static GraphQLContext of(Consumer<GraphQLContext.Builder> contextBuilderConsumer) {
        Builder builder = GraphQLContext.newContext();
        contextBuilderConsumer.accept(builder);
        return of(builder.map);
    }

    /**
     * @return a new and empty graphql context object
     */
    public static GraphQLContext getDefault() {
        return GraphQLContext.newContext().build();
    }

    /**
     * Creates a new GraphqlContext builder
     *
     * @return the new builder
     */
    public static Builder newContext() {
        return new Builder();
    }

    public static class Builder {
        private final ConcurrentMap<Object, Object> map = new ConcurrentHashMap<>();

        public Builder put(
                Object key1, Object value1
        ) {
            return putImpl(
                    key1, value1
            );
        }

        public Builder of(
                Object key1, Object value1
        ) {
            return putImpl(
                    key1, value1
            );
        }

        public Builder of(
                Object key1, Object value1,
                Object key2, Object value2
        ) {
            return putImpl(
                    key1, value1,
                    key2, value2
            );
        }

        public Builder of(
                Object key1, Object value1,
                Object key2, Object value2,
                Object key3, Object value3
        ) {
            return putImpl(
                    key1, value1,
                    key2, value2,
                    key3, value3
            );
        }

        public Builder of(
                Object key1, Object value1,
                Object key2, Object value2,
                Object key3, Object value3,
                Object key4, Object value4
        ) {
            return putImpl(
                    key1, value1,
                    key2, value2,
                    key3, value3,
                    key4, value4
            );
        }

        public Builder of(
                Object key1, Object value1,
                Object key2, Object value2,
                Object key3, Object value3,
                Object key4, Object value4,
                Object key5, Object value5
        ) {
            return putImpl(
                    key1, value1,
                    key2, value2,
                    key3, value3,
                    key4, value4,
                    key5, value5
            );
        }

        /**
         * Adds all of the values in the map into the context builder.  All keys and values MUST be non null
         *
         * @param mapOfContext the map to put into context
         *
         * @return this builder
         */
        public Builder of(Map<?, Object> mapOfContext) {
            assertNotNull(mapOfContext);
            for (Map.Entry<?, Object> entry : mapOfContext.entrySet()) {
                map.put(assertNotNull(entry.getKey()), assertNotNull(entry.getValue()));
            }
            return this;
        }

        /**
         * Adds all of the values in the map into the context builder.  All keys and values MUST be non null
         *
         * @param mapOfContext the map to put into context
         *
         * @return this builder
         */
        public Builder putAll(Map<?, Object> mapOfContext) {
            return of(mapOfContext);
        }

        /**
         * Adds all of the values in the map into the context builder.  All keys and values MUST be non null
         *
         * @param graphQLContext a previous graphql context
         *
         * @return this builder
         */
        public Builder of(GraphQLContext graphQLContext) {
            assertNotNull(graphQLContext);
            return of(graphQLContext.map);
        }

        /**
         * Adds all of the values in the map into the context builder.  All keys and values MUST be non null
         *
         * @param graphQLContextBuilder a graphql context builder
         *
         * @return this builder
         */
        public Builder of(GraphQLContext.Builder graphQLContextBuilder) {
            assertNotNull(graphQLContextBuilder);
            return of(graphQLContextBuilder.build());
        }

        private Builder putImpl(Object... kvs) {
            for (int i = 0; i < kvs.length; i = i + 2) {
                Object k = kvs[i];
                Object v = kvs[i + 1];
                map.put(assertNotNull(k), assertNotNull(v));
            }
            return this;
        }

        public GraphQLContext build() {
            return new GraphQLContext(map);
        }
    }
}
