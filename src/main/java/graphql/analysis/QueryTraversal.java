package graphql.analysis;

import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeUtil;
import graphql.language.NodeVisitor;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.SchemaUtil;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserMarkers;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
public class QueryTraversal {

    private final OperationDefinition operationDefinition;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();
    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final SchemaUtil schemaUtil = new SchemaUtil();
    private final SelectionProvider selectionProvider = new SelectionProvider();

    public QueryTraversal(GraphQLSchema schema,
                          Document document,
                          String operation,
                          Map<String, Object> variables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        
        this.operationDefinition = getOperationResult.operationDefinition;
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.schema = schema;
        this.variables = variables;
    }

    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), getRootType(), null, false);
    }

    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), getRootType(), null, true);
    }

    private GraphQLObjectType getRootType() {
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
        visitPostOrder((env) -> acc[0] = queryReducer.reduceField(env, (T) acc[0]));
        return (T) acc[0];
    }

    @SuppressWarnings("unchecked")
    public <T> T reducePreOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPreOrder((env) -> acc[0] = queryReducer.reduceField(env, (T) acc[0]));
        return (T) acc[0];
    }
        
    private List<Selection> childrenOf (Selection n) {
        return selectionProvider.getChildren(n, fragmentsByName);
    }
    
    private void visitImpl(QueryVisitor visitor, SelectionSet selectionSet, GraphQLCompositeType type, QueryVisitorEnvironment parent, boolean preOrder) {
        QueryTraversalContext context = new QueryTraversalContext(type, parent);
        QueryTraversalDelegate delegate = preOrder
                    ? new QueryTraversalDelegate(visitor::visitField, env -> {})
                    : new QueryTraversalDelegate(env -> {}, visitor::visitField);
        
        // in order not to check parentContext for null and guarantee that
        // we always can obtain the root QueryTraversalContext,
        // we are subclassing here TraverserStack to set up a BARRIER
        // parent that stores root QueryTraversalContext
        new Traverser<Selection>(createStack(context), this::childrenOf)
            .traverse(selectionSet.getSelections(), null, delegate);
    }

    private TraverserStack<Selection> createStack (QueryTraversalContext root) {
        return new TraverserStack<Selection>() {
            @Override
            public void addAll(Collection<? extends Selection> col) {
                TraverserContext<Selection> rootContext = newContext(null, null)
                        .setVar(QueryTraversalContext.class, root);
                addAll(col, rootContext);
            }
        };
    }
    
    private class QueryTraversalDelegate extends NodeVisitorStub<TraverserContext<Selection>> {
        
        final Consumer<QueryVisitorEnvironment> preOrder;
        final Consumer<QueryVisitorEnvironment> postOrder;
        
        QueryTraversalDelegate (Consumer<QueryVisitorEnvironment> preOrder, Consumer<QueryVisitorEnvironment> postOrder) {
            this.preOrder = Assert.assertNotNull(preOrder);
            this.postOrder = Assert.assertNotNull(postOrder);
        }
        
        @Override
        public Object visitInlineFragment(InlineFragment inlineFragment, TraverserContext<Selection> context) {
            if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives()))
                return TraverserMarkers.ABORT;

            // inline fragments are allowed not have type conditions, if so the parent type counts
            QueryTraversalContext parentEnv = context
                    .parentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLCompositeType fragmentCondition;
            if (inlineFragment.getTypeCondition() != null) {
                TypeName typeCondition = inlineFragment.getTypeCondition();
                fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
            } else {
                fragmentCondition = parentEnv.getType();
            }

            // for unions we only have other fragments inside
            return context
                .setVar(QueryTraversalContext.class, new QueryTraversalContext(fragmentCondition, parentEnv.getEnvironment()));
        }

        @Override
        public Object visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Selection> context) {
            if (!conditionalNodes.shouldInclude(variables, fragmentSpread.getDirectives()))
                return TraverserMarkers.ABORT;

            FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
            if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
                return TraverserMarkers.ABORT;

            QueryTraversalContext parentEnv = context
                    .parentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());

            return context
                .setVar(QueryTraversalContext.class, new QueryTraversalContext(typeCondition, parentEnv.getEnvironment()));
        }

        @Override
        public Object visitField(Field field, TraverserContext<Selection> context) {
            if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                return TraverserMarkers.ABORT; // stop recursion

            QueryTraversalContext parentEnv = context
                    .parentContext()
                    .getVar(QueryTraversalContext.class);

            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, parentEnv.getType(), field.getName());
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);

            QueryVisitorEnvironment environment = new QueryVisitorEnvironment(field, fieldDefinition, parentEnv.getType(), parentEnv.getEnvironment(), argumentValues);  
            GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
            QueryTraversalContext fieldEnv = (unmodifiedType instanceof GraphQLCompositeType)
                ? new QueryTraversalContext((GraphQLCompositeType)unmodifiedType, environment)
                : new QueryTraversalContext(null, environment);// Terminal (scalar) node, EMPTY FRAME
            preOrder.accept(fieldEnv.getEnvironment());

            return context
                    .setVar(QueryTraversalContext.class, fieldEnv);
        }

        @Override
        public Object enter(TraverserContext<Node> context, TraverserContext<Selection> data) {
            return context
                    .thisNode()
                    // it is important to pass current traversal context as NodeVisitor's parameter
                    .accept(context, this); 
        }

        @Override
        public Object leave(TraverserContext<Node> context, TraverserContext<Selection> data) {
            return context
                    .thisNode()
                    // it is important to pass current traversal context as NodeVisitor's parameter
                    .accept(context, postOrderVisitor);
        }

        final NodeVisitor<TraverserContext<Selection>> postOrderVisitor = new NodeVisitorStub<TraverserContext<Selection>>() {
            @Override
            public Object visit(Field field, TraverserContext<Selection> context) {
                QueryTraversalContext fieldEnv = context
                        .getVar(QueryTraversalContext.class);
                postOrder.accept(fieldEnv.getEnvironment());

                return context;
            }
        };
    }
}
