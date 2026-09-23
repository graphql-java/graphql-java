package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@PublicApi
@NullMarked
public interface SelectionSetContainer<T extends Node> extends Node<T> {
    @Nullable SelectionSet getSelectionSet();
}
