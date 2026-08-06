package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface Selection<T extends Selection<T>> extends Node<T> {
}
