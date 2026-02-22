package graphql.analysis;

import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.CoercedVariables;
import graphql.execution.RawVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullUnmarked;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static java.util.Collections.singletonList;

/**
 * Helps to traverse (or reduce) a Document (or parts of it) and tracks at the same time the corresponding Schema types.
 * <p>
 * This is an important distinction to just traversing the Document without any type information: Each field has a clearly
 * defined type. See {@link QueryVisitorFieldEnvironment}.
 * <p>
 * Furthermore are the built in Directives skip/include automatically evaluated: if parts of the Document should be ignored they will not
 * be visited. But this is not a full evaluation of a Query: every fragment will be visited/followed regardless of the type condition.
 * <p>
 * It also doesn't consider field merging, which means for example {@code { user{firstName} user{firstName}} } will result in four
 * visitField calls.
 */
@PublicApi
public class QueryTraverser {

    private final Collection<? extends Node> roots;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private CoercedVariables coercedVariables;

    private final GraphQLCompositeType rootParentType;
    private final QueryTraversalOptions options;

    private QueryTraverser(GraphQLSchema schema,
                           Document document,
                           String operation,
                           CoercedVariables coercedVariables,
                           QueryTraversalOptions options
                           ) {
        this.schema = schema;
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.roots = singletonList(getOperationResult.operationDefinition);
        this.rootParentType = getRootTypeFromOperation(getOperationResult.operationDefinition);
        this.coercedVariables = coercedVariables;
        this.options = options;
    }

    private QueryTraverser(GraphQLSchema schema,
                           Document document,
                           String operation,
                           RawVariables rawVariables,
                           QueryTraversalOptions options
    ) {
        this.schema = schema;
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        List<VariableDefinition> variableDefinitions = getOperationResult.operationDefinition.getVariableDefinitions();
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.roots = singletonList(getOperationResult.operationDefinition);
        this.rootParentType = getRootTypeFromOperation(getOperationResult.operationDefinition);
        this.coercedVariables = ValuesResolver.coerceVariableValues(schema, variableDefinitions, rawVariables, GraphQLContext.getDefault(), Locale.getDefault());
        this.options = options;
    }

    private QueryTraverser(GraphQLSchema schema,
                           Node root,
                           GraphQLCompositeType rootParentType,
                           Map<String, FragmentDefinition> fragmentsByName,
                           CoercedVariables coercedVariables,
                           QueryTraversalOptions options
    ) {
        this.schema = schema;
        this.roots = Collections.singleton(root);
        this.rootParentType = rootParentType;
        this.fragmentsByName = fragmentsByName;
        this.coercedVariables = coercedVariables;
        this.options = options;
    }

    public Object visitDepthFirst(QueryVisitor queryVisitor) {
        return visitImpl(queryVisitor, null);
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
     * @return the calculated overall value
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
        OperationDefinition.Operation op = operationDefinition.getOperation() != null
                ? operationDefinition.getOperation()
                : OperationDefinition.Operation.QUERY;
        switch (op) {
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

    private List<Node> childrenOf(Node<?> node) {
        if (!(node instanceof FragmentSpread)) {
            return node.getChildren();
        }
        FragmentSpread fragmentSpread = (FragmentSpread) node;
        return singletonList(fragmentsByName.get(fragmentSpread.getName()));
    }

    private Object visitImpl(QueryVisitor visitFieldCallback, Boolean preOrder) {
        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(QueryTraversalContext.class, new QueryTraversalContext(rootParentType, null, null, GraphQLContext.getDefault()));

        QueryVisitor preOrderCallback;
        QueryVisitor postOrderCallback;
        if (preOrder == null) {
            preOrderCallback = visitFieldCallback;
            postOrderCallback = visitFieldCallback;
        } else {
            QueryVisitor noOp = new QueryVisitorStub();
            preOrderCallback = preOrder ? visitFieldCallback : noOp;
            postOrderCallback = !preOrder ? visitFieldCallback : noOp;
        }

        NodeTraverser nodeTraverser = new NodeTraverser(rootVars, this::childrenOf);
        NodeVisitorWithTypeTracking nodeVisitorWithTypeTracking = new NodeVisitorWithTypeTracking(preOrderCallback,
                postOrderCallback,
                coercedVariables.toMap(),
                schema,
                fragmentsByName,
                options);
        return nodeTraverser.depthFirst(nodeVisitorWithTypeTracking, roots);
    }

    public static Builder newQueryTraverser() {
        return new Builder();
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {
        private GraphQLSchema schema;
        private Document document;
        private String operation;
        private CoercedVariables coercedVariables = CoercedVariables.emptyVariables();
        private RawVariables rawVariables;

        private Node root;
        private GraphQLCompositeType rootParentType;
        private Map<String, FragmentDefinition> fragmentsByName;
        private QueryTraversalOptions options = QueryTraversalOptions.defaultOptions();


        /**
         * The schema used to identify the types of the query.
         *
         * @param schema the schema to use
         *
         * @return this builder
         */
        public Builder schema(GraphQLSchema schema) {
            this.schema = assertNotNull(schema, "schema can't be null");
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
            this.document = assertNotNull(document, "document can't be null");
            return this;
        }

        /**
         * Raw variables used in the query.
         *
         * @param variables the variables to use
         *
         * @return this builder
         */
        public Builder variables(Map<String, Object> variables) {
            assertNotNull(variables, "variables can't be null");
            this.rawVariables = RawVariables.of(variables);
            return this;
        }

        /**
         * Variables (already coerced) used in the query.
         *
         * @param coercedVariables the variables to use
         *
         * @return this builder
         */
        public Builder coercedVariables(CoercedVariables coercedVariables) {
            assertNotNull(coercedVariables, "coercedVariables can't be null");
            this.coercedVariables = coercedVariables;
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
            this.root = assertNotNull(root, "root can't be null");
            return this;
        }

        /**
         * The type of the parent of the root node. (See {@link Builder#root(Node)}
         *
         * @param rootParentType the root parent type
         *
         * @return this builder
         */
        public Builder rootParentType(GraphQLCompositeType rootParentType) {
            this.rootParentType = assertNotNull(rootParentType, "rootParentType can't be null");
            return this;
        }

        /**
         * Fragment by name map. Needs to be provided together with a {@link Builder#root(Node)} and {@link Builder#rootParentType(GraphQLCompositeType)}
         *
         * @param fragmentsByName the map of fragments
         *
         * @return this builder
         */
        public Builder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = assertNotNull(fragmentsByName, "fragmentsByName can't be null");
            return this;
        }

        /**
         * Sets the options to use while traversing
         *
         * @param options the options to use
         * @return this builder
         */
        public Builder options(QueryTraversalOptions options) {
            this.options = assertNotNull(options, "options can't be null");
            return this;
        }

        /**
         * @return a built {@link QueryTraverser} object
         */
        public QueryTraverser build() {
            checkState();
            if (document != null) {
                if (rawVariables != null) {
                    return new QueryTraverser(schema,
                            document,
                            operation,
                            rawVariables,
                            options);
                }
                return new QueryTraverser(schema,
                        document,
                        operation,
                        coercedVariables,
                        options);
            } else {
                if (rawVariables != null) {
                    // When traversing with an arbitrary root, there is no variable definition context available
                    // Thus, the variables must have already been coerced
                    // Retaining this builder for backwards compatibility
                    return new QueryTraverser(schema,
                            root,
                            rootParentType,
                            fragmentsByName,
                            CoercedVariables.of(rawVariables.toMap()),
                            options);
                }
                return new QueryTraverser(schema,
                        root,
                        rootParentType,
                        fragmentsByName,
                        coercedVariables,
                        options);
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
