/*
 * Copyright 2016 Intuit Inc. All rights reserved. Unauthorized reproduction
 * is a violation of applicable law. This material contains certain
 * confidential or proprietary information and trade secrets of Intuit Inc.
 * 
 * graphql.util.Traverser.java
 * 
 * Created: Jan 28, 2018 12:42:17 PM
 * Author: gkesler
 */
package graphql.util;

import static graphql.Assert.assertShouldNeverHappen;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author gkesler
 */
public class Traverser<T> {
    /**
     * Visitor interface that get's notified as the Traverser traverses a tree
     * 
     * @param <T>   type of tree nodes
     * @param <U>   type of data to pass or return across Visitor's methods
     */
    public interface Visitor<T, U> {
        /**
         * Notification that a traverser starts "visiting" a tree node
         * 
         * @param context   traverser context
         * @param data      a value to be passed to the visitor
         * @return          either a value to pass to next Visitor's method during traversal
         *                  or a marker to control the traversal
         * 
         * @see Traverser.Context
         */
        Object enter (Context<T> context, U data);
        /**
         * Notification that a traverser finishes "visiting" a tree node
         * 
         * @param context   traverser context
         * @param data      a value to be passed to the visitor
         * @return          either a value to pass to next Visitor's method during traversal
         *                  or a marker to control the traversal
         * 
         * @see Traverser.Context
         */
        Object leave (Context<T> context, U data);
        
        /**
         * Notification that a traverser visits a node it has already visited
         * This happens in cyclic graphs and the traversal does not traverse this
         * node again to prevent infinite recursion
         * 
         * @param context   traverser context
         * @param data      a value to be passed to the visitor
         * @return          either a value to pass to next Visitor's method during traversal
         *                  or a marker to control the traversal
         * 
         * @see Traverser.Context
         */
        default Object backRef (Context<T> context, U data) {
            return data;
        }
        /**
         * Notification that a traverser visits a map key associated with a child node
         * in case children are stored in a map vs. list. In this case call to this
         * method will be followed by {@link #enter(graphql.util.Traverser.Context, java.lang.Object) method call}
         * 
         * @param context   traverser context
         * @param data      a value to be passed to the visitor
         * @return          either a value to pass to next Visitor's method during traversal
         *                  or a marker to control the traversal
         * 
         * @see Traverser.Context
         */
        default Object mapKey (Context<T> context, U data) {
            return data;
        }
    }
    
    /**
     * Traversal context
     * @param <T> type of tree node
     */
    public interface Context<T> {
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
        Context<T> parentContext();
        /**
         * Informs that the current node has been already "visited"
         * 
         * @param data a value to associate with the node if not yet visited
         * Does not have effect if a node has been already visited
         * 
         * @return {@code true} if a node had been already visited
         */
        boolean isVisited (Object data);
        /**
         * Obtains all visited nodes and values received by the {@link Visitor#enter(graphql.util.Traverser.Context, java.lang.Object) }
         * method
         * 
         * @return a map containg all nodes visited and values passed when visiting nodes for the first time
         */
        Map<T, Object> visitedNodes ();
        /**
         * Obtains a context local variable
         * 
         * @param <S>   type of the variable
         * @param key   key to lookup the variable value
         * 
         * @return a variable value of {@code null}
         */
        <S> S getVar (Class<? super S> key);
        /**
         * Stores a variable in the context
         * 
         * @param <S>   type of a varable
         * @param key   key to create bindings for the variable
         * @param value value of variable
         * 
         * @return this context to allow operations chaining
         */
        <S> Context<T> setVar (Class<? super S> key, S value);
    }
    
    abstract protected static class RecursionState<T> {
        public RecursionState () {
            this(new ArrayDeque<>(32));
        }
        
        public RecursionState (Deque<? super Context<T>> delegate) {
            this.delegate = (Deque<Context<?>>)Objects.requireNonNull(delegate);
        }
        
        public Context<T> peek () {
            return (Context<T>)delegate.peek();
        }
        
        abstract public Context<T> pop ();
        abstract public void  pushAll (Context<T> o, Function<? super T, ? extends List<T>> getChildren);
        
        protected void addAll (Collection<? extends T> col) {
            Objects.requireNonNull(col)
                .stream()
                .map(x -> newContext(x, null))
                .collect(Collectors.toCollection(() -> delegate));
        }
        
        protected boolean isEmpty () {
            return delegate.isEmpty();
        }
        
        protected void clear () {
            delegate.clear();
            visitedMap.clear();
        }
        
        protected Context<T> newContext (final T o, final Context<T> parent) {
            return new Context<T>() {
                @Override
                public T thisNode() {
                    return o;
                }

                @Override
                public Context<T> parentContext() {
                    return parent;
                }

                @Override
                public boolean isVisited(Object data) {
                    return visitedMap.putIfAbsent(o, Optional
                            .ofNullable(data)
                            .orElse(Markers.NULL)) != null;
                }

                @Override
                public Map<T, Object> visitedNodes() {
                    return visitedMap;
                }

                @Override
                public <S> S getVar(Class<? super S> key) {
                    return (S)key.cast(vars.get(key));
                }

                @Override
                public <S> Context<T> setVar(Class<? super S> key, S value) {
                    vars.put(key, value);
                    return this;
                    
                }
                
                final Map<Class<?>, Object> vars = new HashMap<>();
            };
        }
        
        protected final Deque<Context<?>> delegate;
        protected final Map<T, Object> visitedMap = new ConcurrentHashMap<>();
    }
    
    public static class Stack<T> extends RecursionState<T> {
        @Override
        public Context<T> pop() {
            return (Context<T>)delegate.pop();
        }

        @Override
        public void pushAll(Context<T> o, Function<? super T, ? extends List<T>> getChildren) {
            delegate.push(o);
            delegate.push(Markers.END_LIST);
            
            getChildren
                .apply(o.thisNode())
                .stream()
                .collect(Collectors.toCollection(ArrayDeque::new))
                .descendingIterator()
                .forEachRemaining(e -> delegate.push(newContext(e, o)));
        }
    }
    
    public static class Queue<T> extends RecursionState<T> {
        @Override
        public Context<T> pop() {
            return (Context<T>)delegate.remove();
        }

        @Override
        public void pushAll(Context<T> o, Function<? super T, ? extends List<T>> getChildren) {
            getChildren
                .apply(o.thisNode())
                .iterator()
                .forEachRemaining(e -> delegate.add(newContext(e, o)));
            
            delegate.add(Markers.END_LIST);
            delegate.add(o);
        }
    }
    
    /**
     * Special traversal control values
     */
    public enum Markers implements Context<Object> {
        /**
         * Used instead of {@code null} when storing a value in a map
         */
        NULL,
        /**
         * When returned from a Visitor's method, indicates exiting the traversal
         */
        QUIT,
        /**
         * When returned from a Visitor's method, indicates skipping traversal of a subtree
         */
        ABORT,
        /**
         * A special value placed into a {@link RecursionState} to indicate end of children list of
         * a particular node. The very next value in the {@link RecursionState} is the parent node context
         */
        END_LIST,
        /**
         * A special value placed into a {@link RecursionState} to indicate a name/value pair,
         * where the very next element in the {@link RecursionState} is a key immediately followed
         * by a child node stored under that key in its parent's association.
         */
        MAP_KEY;

        @Override
        public Object thisNode() {
            return assertShouldNeverHappen();
        }

        @Override
        public Context<Object> parentContext() {
            return assertShouldNeverHappen();
        }

        @Override
        public boolean isVisited(Object data) {
            return assertShouldNeverHappen();
        }

        @Override
        public Map<Object, Object> visitedNodes() {
            return assertShouldNeverHappen();
        }

        @Override
        public <S> S getVar(Class<? super S> key) {
            return assertShouldNeverHappen();
        }

        @Override
        public <S> Context<Object> setVar(Class<? super S> key, S value) {
            return assertShouldNeverHappen();
        }
    }
    
    /**
     * Instantiates a depth-first Traverser object with a given method to extract
     * children nodes from the current root
     * 
     * @param getChildren a function to extract children
     */
    public Traverser (Function<? super T, ? extends List<T>> getChildren) {
        this(new Stack<>(), getChildren);
    }
    
    /**
     * Instantiates a Traverser object with a given method to extract
     * children nodes from the current root
     * 
     * @param getChildren a function to extract children
     * @param stack       a queue of pended {@link Context} nodes to visit
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
        return new Traverser<>(new Stack<>(), getChildren);
    }
    
    
    /**
     * Creates a standard Traverser suitable for breadth-first traversal
     * 
     * @param <T>           type of tree nodes to0 traverse
     * @param getChildren   a function that obtains a list of children for a given tree node
     * @return              Traverser instance
     */
    public static <T> Traverser<T> breadthFirst (Function<? super T, ? extends List<T>> getChildren) {
        return new Traverser<>(new Queue<>(), getChildren);
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
     * markers {@link Markers}
     * @param visitor a Visitor object to be notified during traversal
     * @return      some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse (T root, U data, Visitor<? super T, ? super U> visitor) {
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
     * markers {@link Markers}
     * @param visitor a Visitor object to be notified during traversal
     * @return      some data produced by the last Visitor's method invoked
     */
    public <U> Object traverse (Collection<T> roots, U data, Visitor<? super T, ? super U> visitor) {
        Objects.requireNonNull(roots);
        Objects.requireNonNull(visitor);
        
        stack.addAll(roots);
        
        Object d = data;
        while (!(stack.isEmpty() || (d = traverseOne((Visitor<T, U>)visitor, (U)d)) == Markers.QUIT));
        
        return d;
    }
    
    protected <U> Object traverseOne (Visitor<T, U> visitor, U data) {
        Context<T> top = stack.pop();
        
        Object result;
        if (top == Markers.END_LIST) {
            // end-of-list marker, we are done recursing children, 
            // mark the current node as fully visited
            result = visitor.leave(stack.pop(), data);
        } else if (top == Markers.MAP_KEY) {
            // in case we've traversed through a map of children,
            // this is a chance to introspect the current child's map key
            result = visitor.mapKey(stack.pop(), data);
        } else if (top.isVisited(data)) {
            // cyclic reference detected
            result = visitor.backRef(top, data);
        } else if ((result = visitor.enter(top, data)) == Markers.ABORT) {
            // abort traversing this subtree, don't recurse to children
        } else if (result == Markers.QUIT) {
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
