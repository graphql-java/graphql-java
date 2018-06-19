package graphql.analysis;

import graphql.PublicApi;

/**
 * Used by {@link QueryTraversal} to reduce the fields of a Document (or part of it) to a single value.
 * <p>
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraversal}.
 * <p>
 * See {@link QueryTraversal#reducePostOrder(QueryReducer, Object)} and {@link QueryTraversal#reducePreOrder(QueryReducer, Object)}
 */
@PublicApi
@FunctionalInterface
public interface QueryReducer<T> {

    /**
     * Called each time a field is visited.
     *
     * @param fieldEnvironment the environment to this call
     * @param acc              the previous result
     *
     * @return the new result
     */
    T reduceField(QueryVisitorFieldEnvironment fieldEnvironment, T acc);
}
