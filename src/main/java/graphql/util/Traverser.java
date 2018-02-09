package graphql.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class Traverser<T> {
    /**
     * Instantiates a depth-first Traverser object with a given method to extract
     * children nodes from the current root
     * 
     * @param getChildren a function to extract children
     */
    public Traverser (Function<? super T, ? extends List<T>> getChildren) {
        this(new TraverserStack<>(), getChildren);
    }
    
    /**
     * Instantiates a Traverser object with a given method to extract
     * children nodes from the current root
     * 
     * @param getChildren a function to extract children
     * @param stack       a queue of pended {@link TraverserContext} nodes to visit
     * <br>
     * * LIFO structure makes the traversal depth-first
     * * FIFO structure makes the traversal breadth-first
     */
    public Traverser (RecursionState<T> stack, Function<? super T, ? extends List<T>> getChildren) {
        this.stack = Objects.requireNonNull(stack);
        this.getChildren = Objects.requireNonNull(getChildren);
    }
    
    /**
     * Creates a standard Traverser suitable for depth-first traversal (both pre- and post- order)
     * 
     * @param <T>           type of tree nodes to0 traverse
     * @param getChildren   a function that obtains a list of children for a given tree node
     * @return              Traverser instance
     */
    public static <T> Traverser<T> depthFirst (Function<? super T, ? extends List<T>> getChildren) {
        return new Traverser<>(new TraverserStack<>(), getChildren);
    }
    
    
    /**
     * Creates a standard Traverser suitable for breadth-first traversal
     * 
     * @param <T>           type of tree nodes to0 traverse
     * @param getChildren   a function that obtains a list of children for a given tree node
     * @return              Traverser instance
     */
    public static <T> Traverser<T> breadthFirst (Function<? super T, ? extends List<T>> getChildren) {
        return new Traverser<>(new TraverserQueue<>(), getChildren);
    }
    
    /**
     * Resets the Traverser to the original state, so it can be re-used
     */
    public void reset () {
        stack.clear();
    }
    
    /**
     * Starts traversal of a tree from a provided root using specified Visitor 
     * and initial data to pass around Visitor's methods
     * 
     * @param <U>   type of data argument to Visitor's methods
     * @param root  subtree root to start traversal from
     * @param data  some data to pass across Visitor's methods. Visitor's methods
     * can change that data to some other values of the same type or special Traverser
     * markers {@link TraverserMarkers}
     * @param visitor a Visitor object to be notified during traversal
     * @return      some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse (T root, U data, TraverserVisitor<? super T, ? super U> visitor) {
        return traverse(Collections.singleton(root), data, visitor);
    }
    
    
    /**
     * Starts traversal of a tree from a provided roots using specified Visitor 
     * and initial data to pass around Visitor's methods
     * 
     * @param <U>   type of data argument to Visitor's methods
     * @param roots  multiple subtree roots to start traversal from
     * @param data  some data to pass across Visitor's methods. Visitor's methods
     * can change that data to some other values of the same type or special Traverser
     * markers {@link TraverserMarkers}
     * @param visitor a Visitor object to be notified during traversal
     * @return      some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse (Collection<T> roots, U data, TraverserVisitor<? super T, ? super U> visitor) {
        Objects.requireNonNull(roots);
        Objects.requireNonNull(visitor);
        
        stack.addAll(roots);
        
        Object d = data;
        while (!(stack.isEmpty() || (d = traverseOne((TraverserVisitor<T, U>)visitor, (U)d)) == TraverserMarkers.QUIT));
        
        return d;
    }
    
    protected <U> Object traverseOne (TraverserVisitor<T, U> visitor, U data) {
        TraverserContext<T> top = stack.pop();
        
        Object result;
        if (top == TraverserMarkers.END_LIST) {
            // end-of-list marker, we are done recursing children, 
            // mark the current node as fully visited
            result = visitor.leave(stack.pop(), data);
        } else if (top == TraverserMarkers.MAP_KEY) {
            // in case we've traversed through a map of children,
            // this is a chance to introspect the current child's map key
            result = visitor.mapKey(stack.pop(), data);
        } else if (top.isVisited(data)) {
            // cyclic reference detected
            result = visitor.backRef(top, data);
        } else if ((result = visitor.enter(top, data)) == TraverserMarkers.ABORT) {
            // abort traversing this subtree, don't recurse to children
        } else if (result == TraverserMarkers.QUIT) {
            // complete abort traversing, don't recurse to children
        } else {
            // recurse to children.
            // Depending on RecursionState implementation we'll either
            // put children into a stack and this will be a depth-first search
            // -or-
            // put children into a queue and this will be a breadth-first search
            stack.pushAll(top, getChildren);
        }        
        
        return result;
    }
    
    protected final RecursionState<T> stack;
    protected final Function<? super T, ? extends List<T>> getChildren;
}
