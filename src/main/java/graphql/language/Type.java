package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface Type<T extends Type> extends Node<T> {

}
