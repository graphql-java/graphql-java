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
import graphql.util.Traverser.Context;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    
    private static class Frame {
        Frame (GraphQLCompositeType type, QueryVisitorEnvironment environment) {
            this.type = type;
            this.environment = environment;
        }

        GraphQLCompositeType type;
        QueryVisitorEnvironment environment;
    }
    
    private void visitImpl(QueryVisitor visitor, SelectionSet selectionSet, GraphQLCompositeType type, QueryVisitorEnvironment parent, boolean preOrder) {
        new Traverser<Selection>(new Traverser.Stack<Selection>() {
                Context<Selection> rootCtx = newContext(null, null).setVar(Frame.class, new Frame(type, parent));

                @Override
                protected Context<Selection> newContext(Selection o, Context<Selection> parent) {
                    return super.newContext(o, Optional
                            .ofNullable(parent)
                            .orElse(rootCtx));
                }
            }, n -> childrenOf(n))
            .traverse(selectionSet.getSelections(), null, new Traverser.Visitor<Selection, Traverser.Context<Selection>>() {
                final NodeVisitor<Traverser.Context<Selection>> preOrderVisitor = new NodeVisitorStub<Traverser.Context<Selection>>() {
                    @Override
                    public Object visit(InlineFragment inlineFragment, Traverser.Context<Selection> context) {
                        if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursing down

                        // inline fragments are allowed not have type conditions, if so the parent type counts
                        Frame top = context
                                .parentContext()
                                .getVar(Frame.class);
                        
                        GraphQLCompositeType fragmentCondition;
                        if (inlineFragment.getTypeCondition() != null) {
                            TypeName typeCondition = inlineFragment.getTypeCondition();
                            fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
                        } else {
                            fragmentCondition = top.type;
                        }
                        
                        // for unions we only have other fragments inside
                        return context
                                .setVar(Frame.class, new Frame(fragmentCondition, top.environment));
                    }

                    @Override
                    public Object visit(FragmentSpread fragmentSpread, Traverser.Context<Selection> context) {
                        if (!conditionalNodes.shouldInclude(variables, fragmentSpread.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
                        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        Frame top = context
                                .parentContext()
                                .getVar(Frame.class);                        
                        
                        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
                        
                        return context
                                .setVar(Frame.class, new Frame(typeCondition, top.environment));
                    }

                    @Override
                    public Object visit(Field field, Traverser.Context<Selection> context) {
                        if (!conditionalNodes.shouldInclude(variables, field.getDirectives()))
                            return Traverser.Markers.ABORT; // stop recursion
                        
                        Frame top = context
                                .parentContext()
                                .getVar(Frame.class);
                        
                        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, top.type, field.getName());
                        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(schema.getFieldVisibility(), fieldDefinition.getArguments(), field.getArguments(), variables);
                        
                        QueryVisitorEnvironment environment = new QueryVisitorEnvironment(field, fieldDefinition, top.type, top.environment, argumentValues);  
                        if (preOrder)
                            visitor.visitField(environment);
                        
                        Frame frame;
                        GraphQLUnmodifiedType unmodifiedType = schemaUtil.getUnmodifiedType(fieldDefinition.getType());
                        if (unmodifiedType instanceof GraphQLCompositeType)
                            frame = new Frame((GraphQLCompositeType)unmodifiedType, environment); 
                        else
                            frame = new Frame(null, environment);// EMPTY FRAME
                        
                        return context
                                .setVar(Frame.class, frame);
                    }
                };
                final NodeVisitor<Traverser.Context<Selection>> postOrderVisitor = new NodeVisitorStub<Traverser.Context<Selection>>() {
                    @Override
                    public Object visit(Field field, Traverser.Context<Selection> context) {
                        Frame top = context.getVar(Frame.class);
                        if (!preOrder)
                            visitor.visitField(top.environment);
                        
                        return context;
                    }
                };
                
                @Override
                public Object enter(Traverser.Context<Selection> context, Traverser.Context<Selection> data) {
                    return context
                            .thisNode()
                            .accept(context, preOrderVisitor);
                }

                @Override
                public Object leave(Traverser.Context<Selection> context, Traverser.Context<Selection> data) {
                    return context
                            .thisNode()
                            .accept(context, postOrderVisitor);
                }
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
