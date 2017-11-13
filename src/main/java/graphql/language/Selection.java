package graphql.language;


public interface Selection<T extends Selection> extends Node<T> {

    /**
     * @return a deep copy of this selection
     */
    T deepCopy();
}
