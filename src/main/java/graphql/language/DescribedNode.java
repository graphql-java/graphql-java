package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a node that can contain a description.
 */
@PublicApi
@NullMarked
public interface DescribedNode<T extends Node> extends Node<T> {

    /**
     * @return the description of this node
     */
    @Nullable Description getDescription();

}
