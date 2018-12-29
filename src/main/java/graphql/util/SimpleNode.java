/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;

/**
 *
 * @author gkesler
 * @param <T>
 */
public class SimpleNode<T> extends Vertex<SimpleNode<T>> { 
    public SimpleNode (T data) {
        this.data = data;
    }
    
    public T getData () {
        return data;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.data);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SimpleNode<?> other = (SimpleNode<?>) obj;
        if (!Objects.equals(this.data, other.data)) {
            return false;
        }
        return true;
    }

    @Override
    protected StringBuilder toString(StringBuilder builder) {
        return super.toString(builder)
            .append(", data=").append(data);
    }
    
    protected final T data;
}
