package graphql.analysis;

import graphql.Internal;

@Internal
@FunctionalInterface
public interface QueryReducer<T> {

    T reduceField(QueryVisitorEnvironment reducerEnvironment, T acc);
}
