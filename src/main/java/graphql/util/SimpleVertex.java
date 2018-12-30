/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;

/**
 * Simple Vertex subtype that can carry a payload of type T
 * 
 * @param <T> type of Vertex payload
 */
public class SimpleVertex<T> extends Vertex<SimpleVertex<T>> { 
    public SimpleVertex (T data) {
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
        final SimpleVertex<?> other = (SimpleVertex<?>) obj;
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
