/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;
import java.util.Objects;
import java.util.function.Function;

interface NodeVertexVisitor<U> {
    default U visit (OperationVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visit (FieldVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visit (DocumentVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visitNode (NodeVertex<? extends Node, ? extends GraphQLType> vertex, U data) {
        return data;
    }
    
    static <U> U whenOperationVertex (NodeVertex<? extends Node, ? extends GraphQLType> node, U data, Function<? super OperationVertex, ? extends U> when) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(when);
        
        return node.accept(data, new NodeVertexVisitor<U>() {
            @Override
            public U visit(OperationVertex node, U data) {
                return when.apply(node);
            }            
        });
    }
    
    static <U> U whenFieldVertex (NodeVertex<? extends Node, ? extends GraphQLType> node, U data, Function<? super FieldVertex, ? extends U> when) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(when);
        
        return node.accept(data, new NodeVertexVisitor<U>() {
            @Override
            public U visit(FieldVertex node, U data) {
                return when.apply(node);
            }            
        });
    }
    
    static <U> U whenDocumentVertex (NodeVertex<? extends Node, ? extends GraphQLType> node, U data, Function<? super DocumentVertex, ? extends U> when) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(when);
        
        return node.accept(data, new NodeVertexVisitor<U>() {
            @Override
            public U visit(DocumentVertex node, U data) {
                return when.apply(node);
            }            
        });
    }
}
