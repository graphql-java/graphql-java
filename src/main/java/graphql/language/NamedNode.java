package graphql.language;


/**
 * Represents a language node that has a name
 */
public interface NamedNode<T extends NamedNode> extends Node<T> {

    /**
     * @return the name of this node
     */
    String getName();
}
