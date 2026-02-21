package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface Definition<T extends Definition> extends Node<T> {

}
