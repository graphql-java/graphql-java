package graphql.schema.impl;

import graphql.Assert;
import graphql.Internal;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLNullableType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.List;

/**
 * A delegating type visitor that allows you to call N visitors in a list
 * and always continues via {@link TraversalControl#CONTINUE}
 */
@Internal
public class MultiReadOnlyGraphQLTypeVisitor implements GraphQLTypeVisitor {

    private final List<GraphQLTypeVisitor> visitors;

    public MultiReadOnlyGraphQLTypeVisitor(List<GraphQLTypeVisitor> visitors) {
        this.visitors = visitors;
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLAppliedDirective(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLAppliedDirectiveArgument(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLArgument(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLInterfaceType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLEnumType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLEnumValueDefinition(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLFieldDefinition(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLDirective(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLInputObjectField(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLInputObjectType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLList(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLNonNull(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLObjectType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLScalarType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLTypeReference(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLUnionType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitBackRef(TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitBackRef(context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLModifiedType(GraphQLModifiedType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLModifiedType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLCompositeType(GraphQLCompositeType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLCompositeType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLDirectiveContainer(GraphQLDirectiveContainer node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLDirectiveContainer(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLFieldsContainer(GraphQLFieldsContainer node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLFieldsContainer(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputFieldsContainer(GraphQLInputFieldsContainer node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLInputFieldsContainer(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputType(GraphQLInputType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLInputType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLNullableType(GraphQLNullableType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLNullableType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLOutputType(GraphQLOutputType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLOutputType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnmodifiedType(GraphQLUnmodifiedType node, TraverserContext<GraphQLSchemaElement> context) {
        visitors.forEach(v -> v.visitGraphQLUnmodifiedType(node, context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl changeNode(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement newChangedNode) {
        return Assert.assertShouldNeverHappen("This must be a read only operation");
    }

    @Override
    public TraversalControl deleteNode(TraverserContext<GraphQLSchemaElement> context) {
        return Assert.assertShouldNeverHappen("This must be a read only operation");
    }

    @Override
    public TraversalControl insertAfter(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement toInsertAfter) {
        return Assert.assertShouldNeverHappen("This must be a read only operation");
    }

    @Override
    public TraversalControl insertBefore(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement toInsertBefore) {
        return Assert.assertShouldNeverHappen("This must be a read only operation");
    }
}
