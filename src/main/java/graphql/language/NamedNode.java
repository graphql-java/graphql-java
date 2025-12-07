package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Represents a language node that has a name
 */
@PublicApi
@NullMarked
public interface NamedNode<T extends NamedNode> extends Node<T> {

    /**
     * @return the name of this node
     */
    String getName();
}
