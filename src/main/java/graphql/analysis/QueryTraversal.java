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
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Helps to traverse (or reduce) a Document (or parts of it) and tracks at the same time the corresponding Schema types.
 * <p>
 * This is an important distinction to just traversing the Document without any type information: Each field has a clearly
 * defined type. See {@link QueryVisitorFieldEnvironment}.
 * <p>
 * Further are the built in Directives skip/include automatically evaluated: if parts of the Document should be ignored they will not
 * be visited. But this is not a full evaluation of a Query: every fragment will be visited/followed regardless of the type condition.
 */
@PublicApi
public class QueryTraversal {

    private final Collection<? extends Node> roots;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();
    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final ChildrenOfSelectionProvider childrenOfSelectionProvider;
    private final GraphQLObjectType rootParentType;

    private QueryTraversal(GraphQLSchema schema,
                           Document document,
                           String operation,
                           Map<String, Object> variables) {
        assertNotNull(document, "document  can't be null");
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        this.schema = assertNotNull(schema, "schema can't be null");
        this.variables = assertNotNull(variables, "variables can't be null");
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.roots = getOperationResult.operationDefinition.getSelectionSet().getChildren();
        this.rootParentType = getRootTypeFromOperation(getOperationResult.operationDefinition);
        this.childrenOfSelectionProvider = new ChildrenOfSelectionProvider(fragmentsByName);
    }

    private QueryTraversal(GraphQLSchema schema,
                           Node root,
                           GraphQLObjectType rootParentType,
                           Map<String, FragmentDefinition> fragmentsByName,
                           Map<String, Object> variables) {
        this.schema = assertNotNull(schema, "schema can't be null");
        this.variables = assertNotNull(variables, "variables can't be null");
        assertNotNull(root, "root can't be null");
        this.roots = Collections.singleton(root);
        this.rootParentType = assertNotNull(rootParentType, "rootParentType can't be null");
        this.fragmentsByName = assertNotNull(fragmentsByName, "fragmentsByName can't be null");
        this.childrenOfSelectionProvider = new ChildrenOfSelectionProvider(fragmentsByName);
    }

    /**
     * Visits the Document (or parts of it) in post-order.
     *
     * @param visitor the query visitor that will be called back
     */
    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, false);
    }

    /**
     * Visits the Document (or parts of it) in pre-order.
     *
     * @param visitor the query visitor that will be called back
     */
    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, true);
    }

    /**
     * Reduces the fields of a Document (or parts of it) to a single value. The fields are visited in post-order.
     *
     * @param queryReducer the query reducer
     * @param initialValue the initial value to pass to the reducer
     * @param <T>          the type of reduced value
     *
     * @return the calculated overall value
     */
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

    /**
     * Reduces the fields of a Document (or parts of it) to a single value. The fields are visited in pre-order.
     *
     * @param queryReducer the query reducer
     * @param initialValue the initial value to pass to the reducer
     * @param <T>          the type of reduced value
     *
     * @return the calucalated overall value
     */
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

            QueryVisitorInlineFragmentEnvironment inlineFragmentEnvironment = new QueryVisitorInlineFragmentEnvironmentImpl(inlineFragment);

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

            QueryVisitorFragmentSpreadEnvironment fragmentSpreadEnvironment = new QueryVisitorFragmentSpreadEnvironmentImpl(fragmentSpread, fragmentDefinition);
            if (context.getVar(LeaveOrEnter.class) == LEAVE) {
                postOrderCallback.visitFragmentSpread(fragmentSpreadEnvironment);
                return TraversalControl.CONTINUE;
            }

            preOrderCallback.visitFragmentSpread(fragmentSpreadEnvironment);

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
            QueryVisitorFieldEnvironment environment = new QueryVisitorFieldEnvironmentImpl(field, fieldDefinition, parentEnv.getType(), parentEnv.getEnvironment(), argumentValues, parentEnv.getSelectionSetContainer());

            LeaveOrEnter leaveOrEnter = context.getVar(LeaveOrEnter.class);
            if (leaveOrEnter == LEAVE) {
                postOrderCallback.visitField(environment);
                return TraversalControl.CONTINUE;
            }

            if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                return TraversalControl.ABORT;

            preOrderCallback.visitField(environment);

            GraphQLUnmodifiedType unmodifiedType = unwrapAll(fieldDefinition.getType());
            QueryTraversalContext fieldEnv = (unmodifiedType instanceof GraphQLCompositeType)
                    ? new QueryTraversalContext((GraphQLCompositeType) unmodifiedType, environment, field)
                    : new QueryTraversalContext(null, environment, field);// Terminal (scalar) node, EMPTY FRAME


            context.setVar(QueryTraversalContext.class, fieldEnv);
            return TraversalControl.CONTINUE;
        }

    }

    public static Builder newQueryTraversal() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private GraphQLSchema schema;
        private Document document;
        private String operation;
        private Map<String, Object> variables;

        private Node root;
        private GraphQLObjectType rootParentType;
        private Map<String, FragmentDefinition> fragmentsByName;


        /**
         * The schema used to identify the types of the query.
         *
         * @param schema the schema to use
         *
         * @return this builder
         */
        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * specify the operation if a document is traversed and there
         * are more than one operation.
         *
         * @param operationName the operation name to use
         *
         * @return this builder
         */
        public Builder operationName(String operationName) {
            this.operation = operationName;
            return this;
        }

        /**
         * document to be used to traverse the whole query.
         * If set a {@link Builder#operationName(String)} might be required.
         *
         * @param document the document to use
         *
         * @return this builder
         */
        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        /**
         * Variables used in the query.
         *
         * @param variables the variables to use
         *
         * @return this builder
         */
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Specify the root node for the traversal. Needs to be provided if there is
         * no {@link Builder#document(Document)}.
         *
         * @param root the root node to use
         *
         * @return this builder
         */
        public Builder root(Node root) {
            this.root = root;
            return this;
        }

        /**
         * The type of the parent of the root node. (See {@link Builder#root(Node)}
         *
         * @param rootParentType the root parent type
         *
         * @return this builder
         */
        public Builder rootParentType(GraphQLObjectType rootParentType) {
            this.rootParentType = rootParentType;
            return this;
        }

        /**
         * Fragment by name map. Needs to be provided together with a {@link Builder#root(Node)} and {@link Builder#rootParentType(GraphQLObjectType)}
         *
         * @param fragmentsByName the map of fragments
         *
         * @return this builder
         */
        public Builder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = fragmentsByName;
            return this;
        }

        /**
         * @return a built {@link graphql.analysis.QueryTraversal} object
         */
        public QueryTraversal build() {
            checkState();
            if (document != null) {
                return new QueryTraversal(schema, document, operation, variables);
            } else {
                return new QueryTraversal(schema, root, rootParentType, fragmentsByName, variables);
            }
        }

        private void checkState() {
            if (document != null || operation != null) {
                if (root != null || rootParentType != null || fragmentsByName != null) {
                    throw new IllegalStateException("ambiguous builder");
                }
            }
        }

    }
}
