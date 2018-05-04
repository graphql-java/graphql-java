package graphql.analysis;

import graphql.Internal;

@Internal
@FunctionalInterface
public interface QueryReducer<T> {

    T reduceField(QueryVisitorFieldEnvironment reducerEnvironment, T acc);
}
