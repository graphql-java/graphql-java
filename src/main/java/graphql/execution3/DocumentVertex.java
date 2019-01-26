/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution.ExecutionStepInfo;
import graphql.language.Document;
import graphql.language.Node;
import graphql.schema.GraphQLType;
import graphql.util.DependencyGraphContext;
import java.util.Objects;

/**
 *
 * @author gkesler
 */
public class DocumentVertex extends NodeVertex<Document, GraphQLType> {
    public DocumentVertex(Document node) {
        super(Objects.requireNonNull(node), null);
    }

    @Override
    public DocumentVertex parentExecutionStepInfo(ExecutionStepInfo parentExecutionStepInfo) {
        return (DocumentVertex)super.parentExecutionStepInfo(parentExecutionStepInfo);
    }

    @Override
    public DocumentVertex executionStepInfo(ExecutionStepInfo value) {
        return (DocumentVertex)super.executionStepInfo(value);
    }

    @Override
    public DocumentVertex source(Object source) {
        return (DocumentVertex)super.source(source);
    }

    @Override
    public DocumentVertex result(Object result) {
        return (DocumentVertex)super.result(result);
    }

    @Override
    <U> U accept(U data, NodeVertexVisitor<? super U> visitor) {
        return (U)visitor.visit(this, data);
    }    
}
