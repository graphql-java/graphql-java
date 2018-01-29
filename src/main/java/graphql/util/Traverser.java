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
    public interface Visitor<T, U> {
        Object enter (Context<? super T> context, U data);
        Object leave (Context<? super T> context, U data);
        
        default Object backRef (Context<? super T> context, U data) {
            return data;
        }
        default Object mapKey (Context<? super T> context, U data) {
            return data;
        }
    }
    
    public interface Context<T> {
        T thisNode();
        Context<T> parentContext();
        boolean isVisited (Object data);
        Map<T, Object> visitedNodes ();
        <S> S getVar (Class<? super S> key);
        <S> void setVar (Class<? super S> key, S value);
    }
    
    abstract protected static class RecursionState<T> {
        public RecursionState () {
            this(new ArrayDeque<>());
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
                            .of(data)
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
                public <S> void setVar(Class<? super S> key, S value) {
                    vars.put(key, value);
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
    
    public enum Markers implements Context<Object> {
        NULL,
        QUIT,
        ABORT,
        END_LIST,
        MAP_KEY;

        @Override
        public Object thisNode() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Context<Object> parentContext() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean isVisited(Object data) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Map<Object, Object> visitedNodes() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <S> S getVar(Class<? super S> key) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <S> void setVar(Class<? super S> key, S value) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
    
    public Traverser (Function<? super T, ? extends List<T>> getChildren) {
        this(new Stack<>(), getChildren);
    }
    
    public Traverser (RecursionState<T> stack, Function<? super T, ? extends List<T>> getChildren) {
        this.stack = Objects.requireNonNull(stack);
        this.getChildren = Objects.requireNonNull(getChildren);
    }
    
    public void reset () {
        stack.clear();
    }
    
    public <U> Object traverse (T root, U data, Visitor<? super T, ? super U> visitor) {
        return traverse(Collections.singleton(root), data, visitor);
    }
    
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
