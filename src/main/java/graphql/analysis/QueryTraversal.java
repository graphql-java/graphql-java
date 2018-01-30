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
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.SchemaUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import graphql.language.Node;
import graphql.language.NodeVisitor;
import graphql.language.NodeVisitorStub;
import graphql.language.Selection;
import graphql.util.Traverser;
import graphql.util.Traverser.Context;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Internal
public class QueryTraversal {


    private final OperationDefinition operationDefinition;
    private final GraphQLSchema schema;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private final Map<String, Object> variables;

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final SchemaUtil schemaUtil = new SchemaUtil();


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
        return (List<Selection>)n.accept(null, new NodeVisitorStub<List<Selection>>() {
            @Override
            public Object visit(InlineFragment node, List<Selection> data) {
                return getChildren(node.getSelectionSet());
            }

            @Override
            public Object visit(FragmentSpread fragmentSpread, List<Selection> data) {
                return getChildren(fragmentsByName.get(fragmentSpread.getName()).getSelectionSet());
            }

            @Override
            public Object visit(Field node, List<Selection> data) {
                return getChildren(node.getSelectionSet());
            }

            @Override
            public Object visit(SelectionSet node, List<Selection> data) {
                return node.getSelections();
            }
            
            private List<Selection> getChildren (SelectionSet node) {
                return (node == null)
                    ? Collections.emptyList()
                    : node.getSelections();
            }
        });
    }
    
    private void visitImpl(QueryVisitor visitor, SelectionSet selectionSet, GraphQLCompositeType type, QueryVisitorEnvironment parent, boolean preOrder) {
        new Traverser<Selection>(this::childrenOf)
            .traverse(selectionSet.getSelections(), null, new NodeVisitorStub<Context<Selection>>() {
                @Override
                public Object visit(InlineFragment inlineFragment, Context<Selection> context) {
                    if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives()))
                        return Traverser.Markers.ABORT; // stop recursing down

                    // inline fragments are allowed not have type conditions, if so the parent type counts
                    Frame top = frames.peek();

                    GraphQLCompositeType fragmentCondition;
                    if (inlineFragment.getTypeCondition() != null) {
                        TypeName typeCondition = inlineFragment.getTypeCondition();
                        fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
                    } else {
                        fragmentCondition = top.type;
                    }

                    // for unions we only have other fragments inside
                    frames.push(new Frame(fragmentCondition, top.environment));
                    return context;
                }

                @Override
                public Object visit(FragmentSpread fragmentSpread, Context<Selection> context) {
                    if (!conditionalNodes.shouldInclude(variables, fragmentSpread.getDirectives()))
                        return Traverser.Markers.ABORT; // stop recursion

                    FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
                    if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
                        return Traverser.Markers.ABORT; // stop recursion

                    Frame top = frames.peek();

                    GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());

                    frames.push(new Frame(typeCondition, top.environment));
                    return context;
                }

                @Override
                public Object visit(Field field, Context<Selection> context) {
                    if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                        return Traverser.Markers.ABORT; // stop recursion

                    Frame top = frames.peek();

                    GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, top.type, field.getName());
                    Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);

                    QueryVisitorEnvironment environment = new QueryVisitorEnvironment(field, fieldDefinition, top.type, top.environment, argumentValues);  
                    visitorNotifier.notifyPreOrder(environment);

                    GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
                    Frame frame = (unmodifiedType instanceof GraphQLCompositeType)
                        ? new Frame((GraphQLCompositeType)unmodifiedType, environment)
                        : new Frame(null, environment);// Terminal (scalar) node, EMPTY FRAME

                    frames.push(frame);
                    return context;
                }

                @Override
                public Object enter(Context<Node> context, Context<Selection> data) {
                    return context
                            .thisNode()
                            .accept(context, this);
                }

                @Override
                public Object leave(Context<Node> context, Context<Selection> data) {
                    return context
                            .thisNode()
                            .accept(context, postOrderVisitor);
                }
        
                final NodeVisitor<Context<Selection>> postOrderVisitor = new NodeVisitorStub<Context<Selection>>() {
                    @Override
                    public Object visit(Field field, Context<Selection> context) {
                        Frame top = frames.pop();
                        visitorNotifier.notifyPostOrder(top.environment);
                        
                        return context;
                    }

                    @Override
                    protected Object visitSelection(Selection<?> node, Context<Selection> context) {
                        frames.pop();
                        return context;
                    }
                };

                class Frame {
                    Frame (GraphQLCompositeType type, QueryVisitorEnvironment environment) {
                        this.type = type;
                        this.environment = environment;
                    }

                    GraphQLCompositeType type;
                    QueryVisitorEnvironment environment;
                }
                
                class QueryVisitorNotifier {
                    QueryVisitorNotifier (Consumer<QueryVisitorEnvironment> preOrder, Consumer<QueryVisitorEnvironment> postOrder) {
                        this.preOrder = Objects.requireNonNull(preOrder);
                        this.postOrder = Objects.requireNonNull(postOrder);
                    }
                    
                    void notifyPreOrder (QueryVisitorEnvironment env) {
                        preOrder.accept(env);
                    }
                    void notifyPostOrder (QueryVisitorEnvironment env) {
                        postOrder.accept(env);
                    }
                    
                    final Consumer<QueryVisitorEnvironment> preOrder, postOrder;
                }
                
                final Deque<Frame> frames = new ArrayDeque<>(Collections.singleton(new Frame(type, parent)));
                final QueryVisitorNotifier visitorNotifier = preOrder
                        ? new QueryVisitorNotifier(visitor::visitField, env -> {})
                        : new QueryVisitorNotifier(env -> {}, visitor::visitField);
            });
    }
}
