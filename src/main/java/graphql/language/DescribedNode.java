package graphql.language;

import graphql.PublicApi;

/**
 * Represents a node that can contain a description.
 */
@PublicApi
public interface DescribedNode<T extends Node> extends Node<T> {

    /**
     * @return the description of this node
     */
    Description getDescription();

}
