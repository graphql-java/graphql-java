package graphql.util;

import java.util.Map;

/**
 * Traversal context
 * @param <T> type of tree node
 */
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
     * by following {@link #parentContext() } links one could obtain
     * the current path as well as the variables {@link #getVar(java.lang.Class) }
     * stored in every parent context.
     *
     * Useful when it is difficult to organize a local Visitor's stack, when performing
     * breadth-first or parallel traversal
     *
     * @return context associated with the node parent
     */
    TraverserContext<T> parentContext();

    /**
     * Informs that the current node has been already "visited"
     *
     * @param data a value to associate with the node if not yet visited
     * Does not have effect if a node has been already visited
     *
     * @return {@code true} if a node had been already visited
     */
    boolean isVisited(Object data);

    /**
     * Obtains all visited nodes and values received by the {@link TraverserVisitor#enter(graphql.util.TraverserContext, java.lang.Object) }
     * method
     *
     * @return a map containg all nodes visited and values passed when visiting nodes for the first time
     */
    Map<T, Object> visitedNodes();

    /**
     * Obtains a context local variable
     *
     * @param <S>   type of the variable
     * @param key   key to lookup the variable value
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
}
