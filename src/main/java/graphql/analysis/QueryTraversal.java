package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.ConditionalNodes;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeTraverser.LeaveOrEnter;
import graphql.language.NodeUtil;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.SchemaUtil;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.NodeTraverser.LeaveOrEnter.LEAVE;

@PublicApi
public class QueryTraversal {

    private final Collection<? extends Node> roots;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();
    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final SchemaUtil schemaUtil = new SchemaUtil();
    private final ChildrenOfSelectionProvider childrenOfSelectionProvider;
    private final GraphQLObjectType rootParentType;

    public QueryTraversal(GraphQLSchema schema,
                          Document document,
                          String operation,
                          Map<String, Object> variables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        this.schema = schema;
        this.variables = variables;
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.roots = getOperationResult.operationDefinition.getSelectionSet().getChildren();
        this.rootParentType = getRootTypeFromOperation(getOperationResult.operationDefinition);
        this.childrenOfSelectionProvider = new ChildrenOfSelectionProvider(fragmentsByName);
    }

    public QueryTraversal(GraphQLSchema schema,
                          Node root,
                          GraphQLObjectType rootParentType,
                          Map<String, FragmentDefinition> fragmentsByName,
                          Map<String, Object> variables) {
        this.schema = schema;
        this.variables = variables;
        this.roots = Collections.singleton(root);
        this.rootParentType = rootParentType;
        this.fragmentsByName = fragmentsByName;
        this.childrenOfSelectionProvider = new ChildrenOfSelectionProvider(fragmentsByName);
    }

    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, false);
    }

    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, true);
    }

    private GraphQLObjectType getRootTypeFromOperation(OperationDefinition operationDefinition) {
        switch (operationDefinition.getOperation()) {
            case MUTATION:
                return assertNotNull(schema.getMutationType());
            case QUERY:
                return assertNotNull(schema.getQueryType());
            case SUBSCRIPTION:
                return assertNotNull(schema.getSubscriptionType());
            default:
                return assertShouldNeverHappen();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T reducePostOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPostOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                acc[0] = queryReducer.reduceField(env, (T) acc[0]);
            }
        });
        return (T) acc[0];
    }

    @SuppressWarnings("unchecked")
    public <T> T reducePreOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPreOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                acc[0] = queryReducer.reduceField(env, (T) acc[0]);
            }
        });
        return (T) acc[0];
    }

    private List<Node> childrenOf(Node selection) {
        return childrenOfSelectionProvider.getSelections((Selection) selection);
    }

    private void visitImpl(QueryVisitor visitFieldCallback, boolean preOrder) {
        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(QueryTraversalContext.class, new QueryTraversalContext(rootParentType, null, null));

        QueryVisitor noOp = new QueryVisitorStub();
        QueryVisitor preOrderCallback = preOrder ? visitFieldCallback : noOp;
        QueryVisitor postOrderCallback = !preOrder ? visitFieldCallback : noOp;

        NodeTraverser nodeTraverser = new NodeTraverser(rootVars, this::childrenOf);
        nodeTraverser.depthFirst(new NodeVisitorImpl(preOrderCallback, postOrderCallback), roots);
    }

    private class NodeVisitorImpl extends NodeVisitorStub {

        final QueryVisitor preOrderCallback;
        final QueryVisitor postOrderCallback;

        NodeVisitorImpl(QueryVisitor preOrderCallback, QueryVisitor postOrderCallback) {
            this.preOrderCallback = preOrderCallback;
            this.postOrderCallback = postOrderCallback;
        }

        @Override
        public TraversalControl visitInlineFragment(InlineFragment inlineFragment, TraverserContext<Node> context) {
            if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives()))
                return TraversalControl.ABORT;

            QueryVisitorInlineFragmentEnvironment inlineFragmentEnvironment = new QueryVisitorInlineFragmentEnvironment(inlineFragment);

            if (context.getVar(LeaveOrEnter.class) == LEAVE) {
                postOrderCallback.visitInlineFragment(inlineFragmentEnvironment);
                return TraversalControl.CONTINUE;
            }

            preOrderCallback.visitInlineFragment(inlineFragmentEnvironment);

            // inline fragments are allowed not have type conditions, if so the parent type counts
            QueryTraversalContext parentEnv = context
                    .getParentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLCompositeType fragmentCondition;
            if (inlineFragment.getTypeCondition() != null) {
                TypeName typeCondition = inlineFragment.getTypeCondition();
                fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
            } else {
                fragmentCondition = parentEnv.getType();
            }
            // for unions we only have other fragments inside
            context.setVar(QueryTraversalContext.class, new QueryTraversalContext(fragmentCondition, parentEnv.getEnvironment(), inlineFragment));
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
            if (!conditionalNodes.shouldInclude(variables, fragmentSpread.getDirectives()))
                return TraversalControl.ABORT;

            FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
            if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
                return TraversalControl.ABORT;

            QueryTraversalContext parentEnv = context
                    .getParentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());

            context
                    .setVar(QueryTraversalContext.class, new QueryTraversalContext(typeCondition, parentEnv.getEnvironment(), fragmentDefinition));
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitField(Field field, TraverserContext<Node> context) {
            QueryTraversalContext parentEnv = context
                    .getParentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, parentEnv.getType(), field.getName());
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);
            QueryVisitorFieldEnvironment environment = new QueryVisitorFieldEnvironment(field, fieldDefinition, parentEnv.getType(), parentEnv.getEnvironment(), argumentValues, parentEnv.getSelectionSetContainer());

            LeaveOrEnter leaveOrEnter = context.getVar(LeaveOrEnter.class);
            if (leaveOrEnter == LEAVE) {
                postOrderCallback.visitField(environment);
                return TraversalControl.CONTINUE;
            }

            if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                return TraversalControl.ABORT;

            preOrderCallback.visitField(environment);

            GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
            QueryTraversalContext fieldEnv = (unmodifiedType instanceof GraphQLCompositeType)
                    ? new QueryTraversalContext((GraphQLCompositeType) unmodifiedType, environment, field)
                    : new QueryTraversalContext(null, environment, field);// Terminal (scalar) node, EMPTY FRAME


            context.setVar(QueryTraversalContext.class, fieldEnv);
            return TraversalControl.CONTINUE;
        }

    }
}
