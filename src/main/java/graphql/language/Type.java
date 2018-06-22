package graphql.language;


public interface Type<T extends Type> extends Node<T> {

    /**
     * @return a deep copy of this type
     */
    T deepCopy();
}
