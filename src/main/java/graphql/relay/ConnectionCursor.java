package graphql.relay;

import graphql.PublicApi;

/**
 * represents a {@link Connection connection} cursor in relay.
 */
@PublicApi
public interface ConnectionCursor {

    String getValue();

}
