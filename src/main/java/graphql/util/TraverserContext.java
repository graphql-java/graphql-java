package graphql.util;

import graphql.PublicApi;

import java.util.List;
import java.util.Set;

/**
 * Traversal context.
 *
 * It is used as providing context for traversing, but also for returning an accumulate value. ({@link #setAccumulate(Object)}
 *
 * There is always a "fake" root context with null node, null parent, null position. See {@link #isRootContext()}
 *
 * @param <T> type of tree node
 */
@PublicApi
public interface TraverserContext<T> {

    /**
     * Returns current node being visited
     *
     * @return current node traverser is visiting. Is null for the root context
     */
    T thisNode();

    /**
     * Change the current node to the provided node. Only applicable in enter.
     *
     * Useful when the tree should be changed while traversing.
     *
     * Also: changing a node makes only a difference when it has different children than the current one.
     *
     * @param newNode the new Node
     */
    void changeNode(T newNode);

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
     * The list of parent nodes starting from the current parent.
     *
     * @return list of parent nodes
     */
    List<T> getParentNodes();

    /**
     * The position of the current node regarding to the parent node.
     *
     * @return the position or null if this node is a root node
     */
    NodePosition getPosition();

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
     * Sets the new accumulate value.
     *
     * Can be retrieved by getA
     *
     * @param accumulate to set
     */
    void setAccumulate(Object accumulate);

    /**
     * The new accumulate value, previously set by {@link #setAccumulate(Object)}
     * or {@link #getCurrentAccumulate()} if {@link #setAccumulate(Object)} not invoked.
     *
     * @return new acc
     */
    <U> U getNewAccumulate();

    /**
     * The current accumulate value used as "input" for the current step.
     *
     * @return current acc
     */
    <U> U getCurrentAccumulate();

    /**
     * Used to share something across all TraverserContext.
     *
     * @return contextData
     */
    <U> U getSharedContextData();

    /**
     * Returns true for the root context, which doesn't have a node or a position.
     *
     * @return true for the root context, otherwise false
     */
    boolean isRootContext();

}
