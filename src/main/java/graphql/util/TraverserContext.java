package graphql.util;

import graphql.PublicApi;

import java.util.Set;

/**
 * Traversal context
 *
 * @param <T> type of tree node
 */
@PublicApi
public interface TraverserContext<T> {
    /**
     * Returns current node being visited
     *
     * @return current node traverser is visiting
     */
    T thisNode();

    /**
     * Returns parent context.
     * Effectively organizes Context objects in a linked list so
     * by following {@link #getParentContext() } links one could obtain
     * the current path as well as the variables {@link #getVar(java.lang.Class) }
     * stored in every parent context.
     *
     * @return context associated with the node parent
     */
    TraverserContext<T> getParentContext();

    /**
     * The result of the {@link #getParentContext()}.
     *
     * @return
     */
    Object getParentResult();

    /**
     * Informs that the current node has been already "visited"
     *
     * @return {@code true} if a node had been already visited
     */
    boolean isVisited();

    /**
     * Obtains all visited nodes and values received by the {@link TraverserVisitor#enter(graphql.util.TraverserContext) }
     * method
     *
     * @return a map containg all nodes visited and values passed when visiting nodes for the first time
     */
    Set<T> visitedNodes();

    /**
     * Obtains a context local variable
     *
     * @param <S> type of the variable
     * @param key key to lookup the variable value
     *
     * @return a variable value of {@code null}
     */
    <S> S getVar(Class<? super S> key);

    /**
     * Stores a variable in the context
     *
     * @param <S>   type of a varable
     * @param key   key to create bindings for the variable
     * @param value value of variable
     *
     * @return this context to allow operations chaining
     */
    <S> TraverserContext<T> setVar(Class<? super S> key, S value);


    /**
     * Set the result for this TraverserContext.
     *
     * @param result
     */
    void setResult(Object result);

    /**
     * The result of this TraverserContext..
     *
     * @return
     */
    Object getResult();

    /**
     * Used to share something across all TraverserContext.
     *
     * @return
     */
    Object getInitialData();

}
