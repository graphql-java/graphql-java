/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.language.Node;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import java.util.Objects;
import java.util.Optional;

/**
 *
 * @author gkesler
 */
public class FieldVertex extends NodeVertex<Field, GraphQLOutputType> {    
    public FieldVertex(Field node, GraphQLOutputType type, GraphQLFieldsContainer definedIn) {
        this(node, type, definedIn, null);
    }    
    
    public FieldVertex(Field node, GraphQLOutputType type, GraphQLFieldsContainer definedIn, NodeVertex<?, ?> inScopeOf) {
        super(Objects.requireNonNull(node), Objects.requireNonNull(type));
        
        this.definedIn = Objects.requireNonNull(definedIn);
        this.inScopeOf = inScopeOf;
    }    

    public GraphQLFieldsContainer getDefinedIn() {
        return definedIn;
    }

    public Object getScope() {
        return inScopeOf;
    }

    public String getResponseKey () {
        return Optional
            .ofNullable(node.getAlias())
            .orElseGet(node::getName);
    }
    
    @Override
    public FieldVertex executionStepInfo(ExecutionStepInfo value) {
        return (FieldVertex)super.executionStepInfo(value);
    }

    @Override
    public FieldVertex source(Object source) {
        return (FieldVertex)super.source(source);
    }

    @Override
    public FieldVertex result(Object result) {
        return (FieldVertex)super.result(result);
    }

    @Override
    public FieldVertex parentExecutionStepInfo(ExecutionStepInfo value) {
        return (FieldVertex)super.parentExecutionStepInfo(value);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.node.getName());
        hash = 97 * hash + Objects.hashCode(this.node.getAlias());
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + Objects.hashCode(this.definedIn);
        hash = 97 * hash + Objects.hashCode(this.inScopeOf);
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            FieldVertex other = (FieldVertex)obj;
            return Objects.equals(this.definedIn, other.definedIn) &&
                    Objects.equals(this.inScopeOf, other.inScopeOf);
        }
        
        return false;
    }

    @Override
    protected StringBuilder toString(StringBuilder builder) {
        return super
                .toString(builder)
                .append(", definedIn=").append(definedIn)
                .append(", inScopeOf=").append(inScopeOf);
    }

    @Override
    <U> U accept(U data, NodeVertexVisitor<? super U> visitor) {
        return (U)visitor.visit(this, data);
    }

    private final GraphQLFieldsContainer definedIn;
    private final Object inScopeOf;
}
