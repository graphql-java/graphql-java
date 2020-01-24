package graphql.language;

public interface DescribedNode<T extends Node> extends Node<T> {

    /**
     * @return the description of this node
     */
    Description getDescription();

}
