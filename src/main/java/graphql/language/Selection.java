package graphql.language;


import graphql.PublicApi;

@PublicApi
public interface Selection<T extends Selection<T>> extends Node<T> {
}
