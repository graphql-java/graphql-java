package graphql.analysis;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.execution.conditional.ConditionalNodes;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.ObjectField;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Locale;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.TraverserContext.Phase.LEAVE;
import static java.lang.String.format;

/**
 * Internally used node visitor which delegates to a {@link QueryVisitor} with type
 * information about the visited field.
 */
@Internal
public class NodeVisitorWithTypeTracking extends NodeVisitorStub {

    private final QueryVisitor preOrderCallback;
    private final QueryVisitor postOrderCallback;
    private final Map<String, Object> variables;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public NodeVisitorWithTypeTracking(QueryVisitor preOrderCallback, QueryVisitor postOrderCallback, Map<String, Object> variables, GraphQLSchema schema, Map<String, FragmentDefinition> fragmentsByName) {
        this.preOrderCallback = preOrderCallback;
        this.postOrderCallback = postOrderCallback;
        this.variables = variables;
        this.schema = schema;
        this.fragmentsByName = fragmentsByName;
    }

    @Override
    public TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {
        // to avoid visiting arguments for directives we abort the traversal here
        return TraversalControl.ABORT;
    }

    @Override
    public TraversalControl visitInlineFragment(InlineFragment inlineFragment, TraverserContext<Node> context) {
        QueryTraversalContext parentEnv = context.getVarFromParents(QueryTraversalContext.class);
        GraphQLContext graphQLContext = parentEnv.getGraphQLContext();
        if (!conditionalNodes.shouldInclude(inlineFragment, variables, null, graphQLContext)) {
            return TraversalControl.ABORT;
        }

        QueryVisitorInlineFragmentEnvironment inlineFragmentEnvironment = new QueryVisitorInlineFragmentEnvironmentImpl(inlineFragment, context, schema);

        if (context.getPhase() == LEAVE) {
            postOrderCallback.visitInlineFragment(inlineFragmentEnvironment);
            return TraversalControl.CONTINUE;
        }

        preOrderCallback.visitInlineFragment(inlineFragmentEnvironment);

        // inline fragments are allowed not have type conditions, if so the parent type counts

        GraphQLCompositeType fragmentCondition;
        if (inlineFragment.getTypeCondition() != null) {
            TypeName typeCondition = inlineFragment.getTypeCondition();
            fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
        } else {
            fragmentCondition = parentEnv.getUnwrappedOutputType();
        }
        // for unions we only have other fragments inside
        context.setVar(QueryTraversalContext.class, new QueryTraversalContext(fragmentCondition, parentEnv.getEnvironment(), inlineFragment, graphQLContext));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentDefinition(FragmentDefinition fragmentDefinition, TraverserContext<Node> context) {
        QueryTraversalContext parentEnv = context.getVarFromParents(QueryTraversalContext.class);
        GraphQLContext graphQLContext = parentEnv.getGraphQLContext();
        if (!conditionalNodes.shouldInclude(fragmentDefinition, variables, null, graphQLContext)) {
            return TraversalControl.ABORT;
        }

        QueryVisitorFragmentDefinitionEnvironment fragmentEnvironment = new QueryVisitorFragmentDefinitionEnvironmentImpl(fragmentDefinition, context, schema);

        if (context.getPhase() == LEAVE) {
            postOrderCallback.visitFragmentDefinition(fragmentEnvironment);
            return TraversalControl.CONTINUE;
        }
        preOrderCallback.visitFragmentDefinition(fragmentEnvironment);

        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        context.setVar(QueryTraversalContext.class, new QueryTraversalContext(typeCondition, parentEnv.getEnvironment(), fragmentDefinition, graphQLContext));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
        QueryTraversalContext parentEnv = context.getVarFromParents(QueryTraversalContext.class);
        GraphQLContext graphQLContext = parentEnv.getGraphQLContext();
        if (!conditionalNodes.shouldInclude(fragmentSpread, variables, null, graphQLContext)) {
            return TraversalControl.ABORT;
        }

        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
        if (!conditionalNodes.shouldInclude(fragmentDefinition, variables, null, graphQLContext)) {
            return TraversalControl.ABORT;
        }

        QueryVisitorFragmentSpreadEnvironment fragmentSpreadEnvironment = new QueryVisitorFragmentSpreadEnvironmentImpl(fragmentSpread, fragmentDefinition, context, schema);
        if (context.getPhase() == LEAVE) {
            postOrderCallback.visitFragmentSpread(fragmentSpreadEnvironment);
            return TraversalControl.CONTINUE;
        }

        preOrderCallback.visitFragmentSpread(fragmentSpreadEnvironment);


        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        assertNotNull(typeCondition,
                () -> format("Invalid type condition '%s' in fragment '%s'", fragmentDefinition.getTypeCondition().getName(),
                        fragmentDefinition.getName()));
        context.setVar(QueryTraversalContext.class, new QueryTraversalContext(typeCondition, parentEnv.getEnvironment(), fragmentDefinition, graphQLContext));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitField(Field field, TraverserContext<Node> context) {
        QueryTraversalContext parentEnv = context.getVarFromParents(QueryTraversalContext.class);
        GraphQLContext graphQLContext = parentEnv.getGraphQLContext();

        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, (GraphQLCompositeType) unwrapAll(parentEnv.getOutputType()), field.getName());
        boolean isTypeNameIntrospectionField = fieldDefinition == schema.getIntrospectionTypenameFieldDefinition();
        GraphQLFieldsContainer fieldsContainer = !isTypeNameIntrospectionField ? (GraphQLFieldsContainer) unwrapAll(parentEnv.getOutputType()) : null;
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry();
        Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(codeRegistry,
                fieldDefinition.getArguments(),
                field.getArguments(),
                CoercedVariables.of(variables),
                GraphQLContext.getDefault(),
                Locale.getDefault());
        QueryVisitorFieldEnvironment environment = new QueryVisitorFieldEnvironmentImpl(isTypeNameIntrospectionField,
                field,
                fieldDefinition,
                parentEnv.getOutputType(),
                fieldsContainer,
                parentEnv.getEnvironment(),
                argumentValues,
                parentEnv.getSelectionSetContainer(),
                context, schema);

        if (context.getPhase() == LEAVE) {
            postOrderCallback.visitField(environment);
            return TraversalControl.CONTINUE;
        }

        if (!conditionalNodes.shouldInclude(field, variables, null, graphQLContext)) {
            return TraversalControl.ABORT;
        }

        TraversalControl traversalControl = preOrderCallback.visitFieldWithControl(environment);

        GraphQLUnmodifiedType unmodifiedType = unwrapAll(fieldDefinition.getType());
        QueryTraversalContext fieldEnv = (unmodifiedType instanceof GraphQLCompositeType)
                ? new QueryTraversalContext(fieldDefinition.getType(), environment, field, graphQLContext)
                : new QueryTraversalContext(null, environment, field, graphQLContext);// Terminal (scalar) node, EMPTY FRAME


        context.setVar(QueryTraversalContext.class, fieldEnv);
        return traversalControl;
    }


    @Override
    public TraversalControl visitArgument(Argument argument, TraverserContext<Node> context) {

        QueryTraversalContext fieldCtx = context.getVarFromParents(QueryTraversalContext.class);
        Field field = (Field) fieldCtx.getSelectionSetContainer();

        QueryVisitorFieldEnvironment fieldEnv = fieldCtx.getEnvironment();
        GraphQLFieldsContainer fieldsContainer = fieldEnv.getFieldsContainer();

        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, fieldsContainer, field.getName());
        GraphQLArgument graphQLArgument = fieldDefinition.getArgument(argument.getName());
        String argumentName = graphQLArgument.getName();

        Object argumentValue = fieldEnv.getArguments().getOrDefault(argumentName, null);

        QueryVisitorFieldArgumentEnvironment environment = new QueryVisitorFieldArgumentEnvironmentImpl(
                fieldDefinition, argument, graphQLArgument, argumentValue, variables, fieldEnv, context, schema);

        QueryVisitorFieldArgumentInputValue inputValue = QueryVisitorFieldArgumentInputValueImpl
                .incompleteArgumentInputValue(graphQLArgument);

        context.setVar(QueryVisitorFieldArgumentEnvironment.class, environment);
        context.setVar(QueryVisitorFieldArgumentInputValue.class, inputValue);
        if (context.getPhase() == LEAVE) {
            return postOrderCallback.visitArgument(environment);
        }
        return preOrderCallback.visitArgument(environment);
    }

    @Override
    public TraversalControl visitObjectField(ObjectField node, TraverserContext<Node> context) {

        QueryVisitorFieldArgumentInputValueImpl inputValue = context.getVarFromParents(QueryVisitorFieldArgumentInputValue.class);
        GraphQLUnmodifiedType unmodifiedType = unwrapAll(inputValue.getInputType());
        //
        // technically a scalar type can have an AST object field - eg field( arg : Json) -> field(arg : { ast : "here" })
        if (unmodifiedType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) unmodifiedType;
            GraphQLInputObjectField inputObjectTypeField = inputObjectType.getField(node.getName());

            inputValue = inputValue.incompleteNewChild(inputObjectTypeField);
            context.setVar(QueryVisitorFieldArgumentInputValue.class, inputValue);
        }
        return TraversalControl.CONTINUE;
    }

    @Override
    protected TraversalControl visitValue(Value<?> value, TraverserContext<Node> context) {
        if (context.getParentNode() instanceof VariableDefinition) {
            visitVariableDefinition(((VariableDefinition) context.getParentNode()), context);
            return TraversalControl.ABORT;
        }

        QueryVisitorFieldArgumentEnvironment fieldArgEnv = context.getVarFromParents(QueryVisitorFieldArgumentEnvironment.class);
        QueryVisitorFieldArgumentInputValueImpl inputValue = context.getVarFromParents(QueryVisitorFieldArgumentInputValue.class);
        // previous visits have set up the previous information
        inputValue = inputValue.completeArgumentInputValue(value);
        context.setVar(QueryVisitorFieldArgumentInputValue.class, inputValue);

        QueryVisitorFieldArgumentValueEnvironment environment = new QueryVisitorFieldArgumentValueEnvironmentImpl(
                schema, fieldArgEnv.getFieldDefinition(), fieldArgEnv.getGraphQLArgument(), inputValue, context,
                variables);

        if (context.getPhase() == LEAVE) {
            return postOrderCallback.visitArgumentValue(environment);
        }
        return preOrderCallback.visitArgumentValue(environment);
    }
}
