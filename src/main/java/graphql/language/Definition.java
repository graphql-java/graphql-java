package graphql.language;


public interface Definition<T extends Definition> extends Node<T> {

    /**
     * @return a deep copy of this definition
     */
    @Override
    T deepCopy();
}
