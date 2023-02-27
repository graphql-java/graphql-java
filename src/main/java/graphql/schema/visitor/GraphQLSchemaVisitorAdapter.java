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

import static graphql.schema.visitor.GraphQLSchemaVisitor.FieldDefinitionVisitorEnvironment;
import static graphql.schema.visitor.GraphQLSchemaVisitor.ObjectVisitorEnvironment;

@Internal
class GraphQLSchemaVisitorAdapter extends GraphQLTypeVisitorStub {

    private final GraphQLSchemaVisitor schemaVisitor;

    GraphQLSchemaVisitorAdapter(GraphQLSchemaVisitor schemaVisitor) {
        this.schemaVisitor = schemaVisitor;
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitAppliedDirectiveArgument(node, new AppliedDirectiveArgumentEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitAppliedDirective(node, new AppliedDirectiveEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitArgument(node, new ArgumentEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
        //
        // we only want to visit directive definitions at the schema level
        // this is our chance to fix up the applied directive problem
        // of one object in two contexts.
        //
        if (context.getParentNode() == null) {
            return schemaVisitor.visitDirective(node, new DirectiveEnv(context));
        }
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitInterfaceType(node, new InterfaceTypeEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitEnumType(node, new EnumTypeEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitEnumValueDefinition(node, new EnumValueDefinitionEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitInputObjectField(node, new InputObjectFieldEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitInputObjectType(node, new InputObjectTypeEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitScalarType(node, new ScalarTypeEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitUnionType(node, new UnionTypeEnv(context));
    }

    @Override
    protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitSchemaElement(node, new SchemaElementEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitFieldDefinition(node, new FieldDefinitionEnv(context));
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return schemaVisitor.visitObjectType(node, new ObjectEnv(context));
    }

    /* ------------------------------
     * GraphQLAppliedDirectiveArgument
     * ------------------------------  */
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

    static class AppliedDirectiveEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLAppliedDirective> implements GraphQLSchemaVisitor.AppliedDirectiveVisitorEnvironment {
        public AppliedDirectiveEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLDirectiveContainer getContainer() {
            return (GraphQLDirectiveContainer) context.getParentNode();
        }
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

    static class DirectiveEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLDirective> implements GraphQLSchemaVisitor.DirectiveVisitorEnvironment {
        public DirectiveEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    static class InterfaceTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLInterfaceType> implements GraphQLSchemaVisitor.InterfaceTypeVisitorEnvironment {
        public InterfaceTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    static class EnumTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLEnumType> implements GraphQLSchemaVisitor.EnumTypeVisitorEnvironment {
        public EnumTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
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


    static class InputObjectTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLInputObjectType> implements GraphQLSchemaVisitor.InputObjectTypeVisitorEnvironment {
        public InputObjectTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    static class ScalarTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLScalarType> implements GraphQLSchemaVisitor.ScalarTypeVisitorEnvironment {
        public ScalarTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    static class UnionTypeEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLUnionType> implements GraphQLSchemaVisitor.UnionTypeVisitorEnvironment {
        public UnionTypeEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }


    static class SchemaElementEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLSchemaElement> implements GraphQLSchemaVisitor.SchemaElementVisitorEnvironment {
        public SchemaElementEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
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

    static class ObjectEnv extends GraphQLSchemaVisitorEnvironmentImpl<GraphQLObjectType> implements ObjectVisitorEnvironment {
        public ObjectEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

}
