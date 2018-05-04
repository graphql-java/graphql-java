package graphql.language;


public interface SelectionSetContainer<T extends Node> extends Node<T> {
    SelectionSet getSelectionSet();
}
