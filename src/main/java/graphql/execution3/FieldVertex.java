/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.TypeTraverser;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
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
                
        Object[] results = {Kind.Object, Cardinality.OneToOne, false/*not null elements*/};
        TypeTraverser.oneVisitWithResult(type, new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLModifiedType(GraphQLModifiedType node, TraverserContext<GraphQLType> context) {
                return node.getWrappedType().accept(context, this);
            }

            @Override
            public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
                results[2] = true;
                return super.visitGraphQLNonNull(node, context);
            }

            @Override
            public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
                results[1] = Cardinality.OneToMany;
                // reset nullable flag as we are looking for nullable elements only
                results[2] = false;
                return super.visitGraphQLList(node, context);
            }

            @Override
            public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLType> context) {
                results[0] = Kind.Scalar;
                return super.visitGraphQLType(node, context);
            }

            @Override
            public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLType> context) {
                results[0] = Kind.Enum;
                return super.visitGraphQLType(node, context);
            }            
        });
        
        this.kind = (Kind)results[0];
        this.cardinality = (Cardinality)results[1];
        this.notNull = (boolean)results[2];
        this.definedIn = Objects.requireNonNull(definedIn);
        this.inScopeOf = inScopeOf;
    }    

    public GraphQLFieldsContainer getDefinedIn() {
        return definedIn;
    }

    public Object getInScopeOf() {
        return inScopeOf;
    }

    public Kind getKind() {
        return kind;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    public boolean isNotNull() {
        return notNull;
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

    public boolean isRoot () {
        return root;
    }
    
    public FieldVertex root (boolean value) {
        this.root = value;
        return this;
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

    public enum Kind {
        Scalar,
        Enum,
        Object
    }
    
    public enum Cardinality {
        OneToOne,
        OneToMany
    }
    
    private final Kind kind;
    private final Cardinality cardinality;
    private final boolean notNull;
    private final GraphQLFieldsContainer definedIn;
    private final Object inScopeOf;
    private /*final*/ boolean root = false;
}
