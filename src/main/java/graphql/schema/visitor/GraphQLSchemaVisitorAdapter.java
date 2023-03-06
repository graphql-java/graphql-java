package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.function.Supplier;

import static graphql.schema.visitor.GraphQLSchemaVisitor.FieldDefinitionVisitorEnvironment;
import static graphql.schema.visitor.GraphQLSchemaVisitor.ObjectVisitorEnvironment;

@Internal
class GraphQLSchemaVisitorAdapter extends GraphQLTypeVisitorStub {

    private final GraphQLSchemaVisitor schemaVisitor;

    GraphQLSchemaVisitorAdapter(GraphQLSchemaVisitor schemaVisitor) {
        this.schemaVisitor = schemaVisitor;
    }

    static class SchemaElementEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLSchemaElement> implements GraphQLSchemaVisitor.SchemaElementVisitorEnvironment {
        public SchemaElementEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    private TraversalControl visitE(TraverserContext<GraphQLSchemaElement> context, Supplier<GraphQLSchemaTraversalControl> visitCall) {

        GraphQLSchemaTraversalControl generalCall = schemaVisitor.visitSchemaElement(context.thisNode(), new SchemaElementEnv(context));
        // if they have changed anything in the general schema element visitation then we don't call the specific visit method
        if (generalCall.isAbortive() || generalCall.isMutative()) {
            return generalCall.toTraversalControl(context);
        }
        GraphQLSchemaTraversalControl specificCall = visitCall.get();
        return specificCall.toTraversalControl(context);
    }

    static class AppliedDirectiveArgumentEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLAppliedDirectiveArgument> implements GraphQLSchemaVisitor.AppliedDirectiveArgumentVisitorEnvironment {
        public AppliedDirectiveArgumentEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLAppliedDirective getContainer() {
            return (GraphQLAppliedDirective) context.getParentNode();
        }

        @Override
        public GraphQLNamedInputType getUnwrappedType() {
            return GraphQLTypeUtil.unwrapAllAs(getElement().getType());
        }
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitAppliedDirectiveArgument(node, new AppliedDirectiveArgumentEnv(context)));
    }

    static class AppliedDirectiveEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLAppliedDirective> implements GraphQLSchemaVisitor.AppliedDirectiveVisitorEnvironment {
        public AppliedDirectiveEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLDirectiveContainer getContainer() {
            return (GraphQLDirectiveContainer) context.getParentNode();
        }
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitAppliedDirective(node, new AppliedDirectiveEnv(context)));
    }

    static class ArgumentEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLArgument> implements GraphQLSchemaVisitor.ArgumentVisitorEnvironment {
        public ArgumentEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLNamedSchemaElement getContainer() {
            return (GraphQLNamedSchemaElement) context.getParentNode();
        }

        @Override
        public GraphQLNamedInputType getUnwrappedType() {
            return GraphQLTypeUtil.unwrapAllAs(getElement().getType());
        }
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitArgument(node, new ArgumentEnv(context)));
    }


    static class DirectiveEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLDirective> implements GraphQLSchemaVisitor.DirectiveVisitorEnvironment {
        public DirectiveEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
        //
        // we only want to visit directive definitions at the schema level
        // this is our chance to fix up the applied directive problem
        // of one class used in two contexts.
        //
        if (context.getParentNode() == null) {
            return visitE(context, () -> schemaVisitor.visitDirective(node, new DirectiveEnv(context)));

        }
        return TraversalControl.CONTINUE;
    }

    static class EnumTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLEnumType> implements GraphQLSchemaVisitor.EnumTypeVisitorEnvironment {
        public EnumTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitEnumType(node, new EnumTypeEnv(context)));
    }

    static class EnumValueDefinitionEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLEnumValueDefinition> implements GraphQLSchemaVisitor.EnumValueDefinitionVisitorEnvironment {
        public EnumValueDefinitionEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLEnumType getContainer() {
            return (GraphQLEnumType) context.getParentNode();
        }
    }

    @Override
    public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitEnumValueDefinition(node, new EnumValueDefinitionEnv(context)));
    }

    static class FieldDefinitionEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLFieldDefinition> implements FieldDefinitionVisitorEnvironment {

        public FieldDefinitionEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLFieldsContainer getContainer() {
            return (GraphQLFieldsContainer) context.getParentNode();
        }

        @Override
        public GraphQLNamedOutputType getUnwrappedType() {
            return GraphQLTypeUtil.unwrapAllAs(getElement().getType());
        }
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitFieldDefinition(node, new FieldDefinitionEnv(context)));
    }

    static class InputObjectFieldEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLInputObjectField> implements GraphQLSchemaVisitor.InputObjectFieldVisitorEnvironment {
        public InputObjectFieldEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLInputObjectType getContainer() {
            return (GraphQLInputObjectType) context.getParentNode();
        }

        @Override
        public GraphQLNamedInputType getUnwrappedType() {
            return GraphQLTypeUtil.unwrapAllAs(getElement().getType());
        }
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitInputObjectField(node, new InputObjectFieldEnv(context)));
    }

    static class InputObjectTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLInputObjectType> implements GraphQLSchemaVisitor.InputObjectTypeVisitorEnvironment {
        public InputObjectTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitInputObjectType(node, new InputObjectTypeEnv(context)));
    }


    static class InterfaceTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLInterfaceType> implements GraphQLSchemaVisitor.InterfaceTypeVisitorEnvironment {
        public InterfaceTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitInterfaceType(node, new InterfaceTypeEnv(context)));
    }

    static class ObjectEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLObjectType> implements ObjectVisitorEnvironment {
        public ObjectEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitObjectType(node, new ObjectEnv(context)));
    }


    static class ScalarTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLScalarType> implements GraphQLSchemaVisitor.ScalarTypeVisitorEnvironment {
        public ScalarTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitScalarType(node, new ScalarTypeEnv(context)));
    }

    static class UnionTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLUnionType> implements GraphQLSchemaVisitor.UnionTypeVisitorEnvironment {
        public UnionTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitE(context, () -> schemaVisitor.visitUnionType(node, new UnionTypeEnv(context)));
    }
}
