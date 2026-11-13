package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a language node that has a name
 */
@PublicApi
@NullMarked
public interface NamedNode<T extends NamedNode> extends Node<T> {

    /**
     * @return the name of this node, or null if this node is anonymous (e.g. an anonymous operation definition)
     */
    @Nullable String getName();
}
