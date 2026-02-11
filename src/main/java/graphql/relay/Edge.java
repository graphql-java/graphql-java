package graphql.relay;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents an edge in Relay which is essentially a node of data T and the cursor for that node.
 * <p>
 * See <a href="https://facebook.github.io/relay/graphql/connections.htm#sec-Edge-Types">https://facebook.github.io/relay/graphql/connections.htm#sec-Edge-Types</a>
 */
@PublicApi
@NullMarked
public interface Edge<T> {

    /**
     * @return the node of data that this edge represents, or null if the node failed to resolve
     */
    @Nullable T getNode();

    /**
     * @return the cursor for this edge node
     */
    ConnectionCursor getCursor();

}
