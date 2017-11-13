package graphql.language;


public interface Value<T extends Value> extends Node<T> {

    /**
     * @return a deep copy of this value
     */
    T deepCopy();
}
