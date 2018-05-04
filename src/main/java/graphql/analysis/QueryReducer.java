package graphql.analysis;

import graphql.PublicApi;

@PublicApi
@FunctionalInterface
public interface QueryReducer<T> {

    T reduceField(QueryVisitorFieldEnvironment reducerEnvironment, T acc);
}
