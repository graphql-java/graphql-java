package graphql.util;

import graphql.Internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TraversalControl.QUIT;

@Internal
public class Traverser<T> {

    private final TraverserState<T> traverserState;
    private final Function<? super T, ? extends List<T>> getChildren;
    private final Map<Class<?>, Object> rootVars = new ConcurrentHashMap<>();

    private static final List<TraversalControl> CONTINUE_OR_QUIT = Arrays.asList(CONTINUE, QUIT);

    private Traverser(TraverserState<T> traverserState, Function<? super T, ? extends List<T>> getChildren) {
        this.traverserState = assertNotNull(traverserState);
        this.getChildren = assertNotNull(getChildren);
    }

    public Traverser<T> rootVars(Map<Class<?>, Object> rootVars) {
        this.rootVars.putAll(assertNotNull(rootVars));
        return this;
    }

    public Traverser<T> rootVar(Class<?> key, Object value) {
        rootVars.put(key, value);
        return this;
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return depthFirst(getChildren, null);
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren, Object initialData) {
        return new Traverser<>(TraverserState.newStackState(initialData), getChildren);
    }


    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return breadthFirst(getChildren, null);
    }

    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren, Object initialData) {
        return new Traverser<>(TraverserState.newQueueState(initialData), getChildren);
    }


    public TraverserResult traverse(T root, TraverserVisitor<? super T> visitor) {
        return traverse(Collections.singleton(root), visitor);
    }


    public TraverserResult traverse(Collection<? extends T> roots, TraverserVisitor<? super T> visitor) {
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
                TraverserContext contextForLeave = (TraverserContext) traverserState.pop();
                currentContext = contextForLeave;
                TraversalControl traversalControl = visitor.leave(contextForLeave);
                assertNotNull(traversalControl, "result of leave must not be null");
                assertTrue(CONTINUE_OR_QUIT.contains(traversalControl), "result can only return CONTINUE or QUIT");
                switch (traversalControl) {
                    case QUIT:
                        break traverseLoop;
                    case CONTINUE:
                        continue;
                    default:
                        assertShouldNeverHappen();
                }
            }

            currentContext = (TraverserContext) top;

            if (currentContext.isVisited()) {
                TraversalControl traversalControl = visitor.backRef(currentContext);
                assertNotNull(traversalControl, "result of backRef must not be null");
                assertTrue(CONTINUE_OR_QUIT.contains(traversalControl), "backRef can only return CONTINUE or QUIT");
                if (traversalControl == QUIT) {
                    break traverseLoop;
                }
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
        TraverserResult traverserResult = new TraverserResult(currentContext.getResult());
        return traverserResult;
    }


}
