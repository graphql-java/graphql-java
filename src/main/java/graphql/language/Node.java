package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.io.Serializable;
import java.util.List;

/**
 * The base interface for virtually all graphql language elements
 *
 * NOTE: This class implements {@link java.io.Serializable} and hence it can be serialised and placed into a distributed cache.  However we
 * are not aiming to provide long term compatibility and do not intend for you to place this serialised data into permanent storage,
 * with times frames that cross graphql-java versions.  While we don't change things unnecessarily,  we may inadvertently break
 * the serialised compatibility across versions.
 */
@PublicApi
public interface Node<T extends Node> extends Serializable {

    /**
     * @return a list of the children of this node
     */
    List<Node> getChildren();

    /**
     * @return the source location where this node occurs
     */
    SourceLocation getSourceLocation();

    /**
     * Nodes can have comments made on them, the following is one comment per line before a node.
     *
     * @return the list of comments or an empty list of there are none
     */
    List<Comment> getComments();

    /**
     * Compares just the content and not the children.
     *
     * @param node the other node to compare to
     *
     * @return isEqualTo
     */
    boolean isEqualTo(Node node);

    /**
     * @return a deep copy of this node
     */
    T deepCopy();

    /**
     * Double-dispatch entry point.
     * A node receives a Visitor instance and then calls a method on a Visitor
     * that corresponds to a actual type of this Node. This binding however happens
     * at the compile time and therefore it allows to save on rather expensive
     * reflection based {@code instanceOf} check when decision based on the actual
     * type of Node is needed, which happens redundantly during traversing AST.
     *
     * Additional advantage of this pattern is to decouple tree traversal mechanism
     * from the code that needs to be executed when traversal "visits" a particular Node
     * in the tree. This leads to a better code re-usability and maintainability.
     *
     * @param context TraverserContext bound to this Node object
     * @param visitor Visitor instance that performs actual processing on the Nodes(s)
     *
     * @return Result of Visitor's operation.
     * Note! Visitor's operation might return special results to control traversal process.
     */
    TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor);
}
