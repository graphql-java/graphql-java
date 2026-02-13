package graphql.relay;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * This represents a connection in Relay, which is a list of {@link graphql.relay.Edge edge}s
 * as well as a {@link graphql.relay.PageInfo pageInfo} that describes the pagination of that list.
 * <p>
 * See <a href="https://relay.dev/graphql/connections.htm">https://relay.dev/graphql/connections.htm</a>
 */
@PublicApi
@NullMarked
public interface Connection<T> {

    /**
     * @return a list of {@link graphql.relay.Edge}s that contain a node of data and its cursor. Can be null as defined in the spec.
     */
    @Nullable List<Edge<T>> getEdges();

    /**
     * @return {@link graphql.relay.PageInfo} pagination data about that list of edges. Not nullable by definition in the spec.
     */
    PageInfo getPageInfo();

}
