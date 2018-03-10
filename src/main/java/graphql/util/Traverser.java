package graphql.util;

import graphql.Internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
public class Traverser<T> {

    private final TraverserState<T> traverserState;
    private final Function<? super T, ? extends List<T>> getChildren;
    private final Map<Class<?>, Object> rootVars = new ConcurrentHashMap<>();

    /**
     * Instantiates a Traverser object with a given method to extract
     * children nodes from the current root
     *
     * @param getChildren    a function to extract children
     * @param traverserState a queue of pended {@link TraverserContext} nodes to visit
     *                       <br>
     *                       * LIFO structure makes the traversal depth-first
     *                       * FIFO structure makes the traversal breadth-first
     */
    private Traverser(TraverserState<T> traverserState, Function<? super T, ? extends List<T>> getChildren) {
        this.traverserState = assertNotNull(traverserState);
        this.getChildren = assertNotNull(getChildren);
    }

    /**
     * Bootstraps the very root (BARRIER) TraverserContext as a common parent for
     * all traversal roots' contexts with the provided set of root variables
     *
     * @param rootVars root variables
     *
     * @return this Traverser instance to allow chaining
     */
    public Traverser<T> rootVars(Map<Class<?>, Object> rootVars) {
        this.rootVars.putAll(assertNotNull(rootVars));
        return this;
    }

    /**
     * Bootstraps the very root (BARRIER) TraverserContext as a common parent for
     * all traversal roots' contexts with the provided root variable
     *
     * @param key   key to store root variable
     * @param value value of the root variable
     *
     * @return this Traverser instance to allow chaining
     */
    public Traverser<T> rootVar(Class<?> key, Object value) {
        rootVars.put(key, value);
        return this;
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return depthFirst(getChildren, null);
    }

    /**
     * Creates a standard Traverser suitable for depth-first traversal (both pre- and post- order)
     *
     * @param <T>         type of tree nodes to0 traverse
     * @param getChildren a function that obtains a list of children for a given tree node
     * @param initialData some data to passed into the traversal at the bootstrap time
     *
     * @return Traverser instance
     */
    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren, Object initialData) {
        return new Traverser<>(TraverserState.newStackState(initialData), getChildren);
    }


    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return breadthFirst(getChildren, null);
    }

    /**
     * Creates a standard Traverser suitable for breadth-first traversal
     *
     * @param <T>         type of tree nodes to0 traverse
     * @param getChildren a function that obtains a list of children for a given tree node
     * @param initialData some data to passed into the traversal at the bootstrap time
     *
     * @return Traverser instance
     */
    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren, Object initialData) {
        return new Traverser<>(TraverserState.newQueueState(initialData), getChildren);
    }


    /**
     * Starts traversal of a tree from a provided root using specified Visitor
     * and initial data to pass around Visitor's methods
     *
     * @param <U>     type of data argument to Visitor's methods
     * @param root    subtree root to start traversal from
     * @param visitor a Visitor object to be notified during traversal
     *
     * @return some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse(T root, TraverserVisitor<? super T> visitor) {
        return traverse(Collections.singleton(root), visitor);
    }


    /**
     * Starts traversal of a tree from a provided roots using specified Visitor
     * and initial data to pass around Visitor's methods
     *
     * @param <U>     type of data argument to Visitor's methods
     * @param roots   multiple subtree roots to start traversal from
     *                can change that data to some other values of the same type or special Traverser
     *                markers {@link TraversalControl}
     * @param visitor a Visitor object to be notified during traversal
     *
     * @return some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse(Collection<? extends T> roots, TraverserVisitor<? super T> visitor) {
        assertNotNull(roots);
        assertNotNull(visitor);

        traverserState.addNewContexts(roots, traverserState.newContext(null, null, rootVars));

        TraverserContext currentContext = null;
        traverseLoop:
        while (!traverserState.isEmpty()) {
            Object top = traverserState.pop();

            if (top == TraverserState.Marker.END_LIST) {
                // end-of-list marker, we are done recursing children,
                // mark the current node as fully visited
                TraversalControl traversalControl = visitor.leave((TraverserContext) traverserState.pop());
                assertNotNull(traversalControl, "result of leave must not be null");
                switch (traversalControl) {
                    case QUIT:
                        break traverseLoop;
                    case ABORT:
                    case CONTINUE:
                        continue;
                    default:
                        assertShouldNeverHappen();
                }
            }

            currentContext = (TraverserContext) top;
            if (currentContext.isVisited()) {
                visitor.backRef(currentContext);
            } else {
                TraversalControl traversalControl = visitor.enter(currentContext);
                assertNotNull(traversalControl, "result of enter must not be null");
                this.traverserState.addVisited((T) currentContext.thisNode());
                switch (traversalControl) {
                    case QUIT:
                        break traverseLoop;
                    case ABORT:
                        continue;
                    case CONTINUE:
                        traverserState.pushAll(currentContext, getChildren);
                        continue;
                    default:
                        assertShouldNeverHappen();
                }
            }
        }
        return currentContext.getResult();
    }


}
