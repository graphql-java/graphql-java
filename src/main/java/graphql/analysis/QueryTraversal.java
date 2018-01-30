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
import graphql.language.NodeVisitor;
import graphql.language.NodeVisitorStub;
import graphql.language.Selection;
import graphql.util.Traverser;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

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
        return (List<Selection>)n.accept(Collections.emptyList(), new NodeVisitorStub<List<Selection>>() {
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
        new Traverser<Selection>(n -> childrenOf(n))
            .traverse(selectionSet.getSelections(), null, new Traverser.Visitor<Selection, Void>() {
                final NodeVisitor<Void> preOrderVisitor = new NodeVisitorStub<Void>() {
                    @Override
                    public Object visit(InlineFragment inlineFragment, Void data) {
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
                        return data;
                    }

                    @Override
                    public Object visit(FragmentSpread fragmentSpread, Void data) {
                        if (!conditionalNodes.shouldInclude(variables, fragmentSpread.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
                        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        Frame top = frames.peek();                        
                        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
                        
                        frames.push(new Frame(typeCondition, top.environment));
                        return data;
                    }

                    @Override
                    public Object visit(Field field, Void data) {
                        if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        Frame top = frames.peek();
                        
                        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, top.type, field.getName());
                        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);
                        
                        QueryVisitorEnvironment environment = new QueryVisitorEnvironment(field, fieldDefinition, top.type, top.environment, argumentValues);  
                        if (preOrder)
                            visitor.visitField(environment);
                        
                        GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
                        if (unmodifiedType instanceof GraphQLCompositeType)
                            frames.push(new Frame((GraphQLCompositeType)unmodifiedType, environment)); 
                        else
                            frames.push(new Frame(null, environment));// EMPTY FRAME
                        
                        return data;
                    }
                };
                final NodeVisitor<Void> postOrderVisitor = new NodeVisitorStub<Void>() {
                    @Override
                    protected Object visitSelection(Selection<?> node, Void data) {
                        frames.pop();
                        return data;
                    }

                    @Override
                    public Object visit(Field field, Void data) {
                        Frame top = frames.pop();
                        if (!preOrder)
                            visitor.visitField(top.environment);
                        
                        return data;
                    }
                };
                
                @Override
                public Object enter(Traverser.Context<Selection> context, Void data) {
                    return context.thisNode().accept(data, preOrderVisitor);
                }

                @Override
                public Object leave(Traverser.Context<Selection> context, Void data) {
                    return context.thisNode().accept(data, postOrderVisitor);
                }
                
                class Frame {
                    Frame (GraphQLCompositeType type, QueryVisitorEnvironment environment) {
                        this.type = type;
                        this.environment = environment;
                    }
                    
                    GraphQLCompositeType type;
                    QueryVisitorEnvironment environment;
                }
                Deque<Frame> frames = new ArrayDeque<>(Collections.singleton(new Frame(type, parent)));
            });
    }
/*
    private void visitFragmentSpread(QueryVisitor visitor, FragmentSpread fragmentSpread, QueryVisitorEnvironment parent, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(this.variables, fragmentSpread.getDirectives())) {
            return; // stop recursion
        }
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives())) {
            return; // stop recursion
        }
        
        // recurse to the selections
        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        visitImpl(visitor, fragmentDefinition.getSelectionSet(), typeCondition, parent, preOrder);
    }


    private void visitInlineFragment(QueryVisitor visitor, InlineFragment inlineFragment, GraphQLCompositeType parentType, QueryVisitorEnvironment parent, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives())) {
            return;
        }
        // inline fragments are allowed not have type conditions, if so the parent type counts
        GraphQLCompositeType fragmentCondition;
        if (inlineFragment.getTypeCondition() != null) {
            TypeName typeCondition = inlineFragment.getTypeCondition();
            fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
        } else {
            fragmentCondition = parentType;
        }
        // for unions we only have other fragments inside
        visitImpl(visitor, inlineFragment.getSelectionSet(), fragmentCondition, parent, preOrder);
    }

    private void visitField(QueryVisitor visitor, Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parentType, QueryVisitorEnvironment parentEnv, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(variables, field.getDirectives())) {
            return;
        }
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);
        if (preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues));
        }
        GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
        if (unmodifiedType instanceof GraphQLCompositeType) {
            QueryVisitorEnvironment newParentEnvironment = new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues);
            visitImpl(visitor, field.getSelectionSet(), (GraphQLCompositeType) unmodifiedType, newParentEnvironment, preOrder);
        }
        if (!preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues));
        }

    }
*/
}
