package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface Value<T extends Value> extends Node<T> {

}
