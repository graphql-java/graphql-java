/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution.ConditionalNodes;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.UnknownOperationException;
import graphql.execution.ValuesResolver;
import graphql.execution2.Common;
import graphql.introspection.Introspection;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.DependencyGraph;
import graphql.util.DependencyGraphContext;
import graphql.util.Edge;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author gkesler
 */
class ExecutionPlan extends DependencyGraph<NodeVertex<Node, GraphQLType>> {    
    public GraphQLSchema getSchema() {
        return schema;
    }

    public Document getDocument() {
        return document;
    }
    
    public OperationDefinition getOperation (String operationName) {
        if (operationName == null && operationsByName.size() > 1)
            throw new UnknownOperationException("Must provide operation name if query contains multiple operations.");
        
        return Optional
            .ofNullable(operationsByName.get(operationName))
            .orElseThrow(() -> new UnknownOperationException(String.format("Unknown operation named '%s'.", operationName)));
    }

    public Map<String, OperationDefinition> getOperationsByName() {
        return Collections.unmodifiableMap(operationsByName);
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return Collections.unmodifiableMap(fragmentsByName);
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    protected void prepareResolve (ExecutionPlanContext context, Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
        context.prepareResolve(edge);
    }

    protected void whenResolved (ExecutionPlanContext context, Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
        context.whenResolved(edge);
    }
    
    static Builder newExecutionPlanBuilder () {
        return new Builder(new ExecutionPlan());
    }
    
    static class Builder extends NodeVisitorStub {
        private Builder (ExecutionPlan executionPlan) {
            this.executionPlan = Objects.requireNonNull(executionPlan);
        }
        
        public Builder schema (GraphQLSchema schema) {
            executionPlan.schema = Objects.requireNonNull(schema);
            return this;
        }

        public Builder document (Document document) {
            executionPlan.document = Objects.requireNonNull(document);

            // to optimize a bit on searching for operations and fragments,
            // let's re-organize this a little
            // FIXME: re-organize Document node to keep operations and fragments indexed
            executionPlan.operationsByName = new HashMap<>();
            executionPlan.fragmentsByName = new HashMap<>();

            document
                .getDefinitions()
                .forEach(definition -> NodeTraverser.oneVisitWithResult(definition, new NodeVisitorStub() {
                    @Override
                    public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
                        executionPlan.operationsByName.put(node.getName(), node);
                        return TraversalControl.QUIT;
                    }

                    @Override
                    public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
                        executionPlan.fragmentsByName.put(node.getName(), node);
                        return TraversalControl.QUIT;
                    }
                }));

            return this;
        }

        public Builder operation (String operationName) {
            operations.add(executionPlan.getOperation(operationName));
            return this;
        }

        public Builder variables (Map<String, Object> variables) {
            executionPlan.variables = Objects.requireNonNull(variables);
            return this;
        }

        private List<Node> getChildrenOf (Node node) {
            return NodeTraverser.oneVisitWithResult(node, new NodeVisitorStub() {
                @Override
                public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                    Collection<Node> children = Optional
                        .ofNullable(executionPlan.fragmentsByName.get(node.getName()))
                        .map(Node::getChildren)
                        // per https://facebook.github.io/graphql/June2018/#sec-Field-Collection d.v.
                        .orElseGet(Collections::emptyList);
                    
                    context.setResult(children);
                    return TraversalControl.QUIT;
                }            

                @Override
                protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
                    context.setResult(node.getChildren());
                    return TraversalControl.QUIT;
                }
            });
        }

        public ExecutionPlan build () {  
            Objects.requireNonNull(executionPlan.schema);

            // fix default operation if wasn't provided
            if (operations.isEmpty())
                operation(null);

            // validate variables against all selected operations
            // Note! coerceArgumentValues throws a RuntimeException to be handled later
            ValuesResolver valuesResolver = new ValuesResolver();
            List<VariableDefinition> variableDefinitions = operations
                    .stream()
                    .flatMap(od -> od.getVariableDefinitions().stream())
                    .collect(Collectors.toList());
            executionPlan.variables = valuesResolver.coerceArgumentValues(executionPlan.schema, variableDefinitions, executionPlan.variables);

            NodeVertex<Node, GraphQLType> documentVertex = executionPlan.addNode(toNodeVertex(new DocumentVertex(executionPlan.document)));
            Map<Class<?>, Object> rootVars = Collections.singletonMap(DocumentVertex.class, documentVertex);
            
            // walk Operations ASTs to record dependencies between fields
            NodeTraverser traverser = new NodeTraverser(rootVars, this::getChildrenOf, executionPlan);
            traverser.depthFirst(this, operations, Builder::newTraverserState);

            return executionPlan;
        }

        private static TraverserState.StackTraverserState<Node> newTraverserState (Object initialData) {
            return TraverserState.newStackState(initialData, Builder::newTraverserContext);
        }

        private static TraverserContext<Node> newTraverserContext (TraverserState.TraverserContextBuilder<Node> builder) {
            Objects.requireNonNull(builder);

            Node curNode = builder.getNode();
            TraverserContext<Node> parent = builder.getParentContext();
            Map<Class<?>, Object> vars = builder.getVars();
            Set<Node> visited = builder.getVisited();
            Object initialData = builder.getInitialData();

            return new TraverserContext<Node>() {
                Object result;

                @Override
                public Node thisNode() {
                    return curNode;
                }

                @Override
                public TraverserContext<Node> getParentContext() {
                    return parent;
                }

                @Override
                public Set<Node> visitedNodes() {
                    return visited;
                }

                @Override
                public <S> S getVar(Class<? super S> key) {
                    return (S)vars.computeIfAbsent(key, k -> Optional
                            .ofNullable(parent)
                            .map(p -> p.getVar((Class<S>)k))
                            .orElse(null));
                }

                @Override
                public <S> TraverserContext<Node> setVar(Class<? super S> key, S value) {
                    vars.put(key, value);
                    return this;
                }

                @Override
                public void setResult(Object result) {
                    this.result = result;
                }

                @Override
                public Object getResult() {
                    return Optional
                        .ofNullable(result)
                        .orElseGet(() -> result = getParentResult());
                }

                @Override
                public Object getInitialData() {
                    return initialData;
                }
            };
        }

        private OperationVertex newOperationVertex (OperationDefinition operationDefinition) {
            GraphQLObjectType operationType = Common.getOperationRootType(executionPlan.schema, operationDefinition);
            return new OperationVertex(operationDefinition, operationType);
        }

        private FieldVertex newFieldVertex (Field field, GraphQLObjectType parentType, FieldVertex inScopeOf) {
            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionPlan.schema, (GraphQLCompositeType)GraphQLTypeUtil.unwrapNonNull(parentType), field.getName());
            return new FieldVertex(field, fieldDefinition.getType(), parentType, inScopeOf);
        }

        private <V extends NodeVertex<? extends Node, ? extends GraphQLType>> DependencyGraph<? super V> executionPlan (TraverserContext<Node> context) {
            return (DependencyGraph<? super V>)context.getInitialData();
        }

        private static <V extends NodeVertex<? extends Node, ? extends GraphQLType>> NodeVertex<? super Node, ? super GraphQLType> toNodeVertex (V vertex) {
            return (NodeVertex<Node, GraphQLType>)vertex;
        }

        private boolean isFieldVertex (NodeVertex<? extends Node, ? extends GraphQLType> vertex) {
            return vertex.accept(false, IS_FIELD);
        }
        
        // NodeVisitor methods

        @Override
        public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
            switch (context.getVar(NodeTraverser.LeaveOrEnter.class)) {
                case ENTER: {
                    OperationVertex vertex = (OperationVertex)this.<OperationVertex>executionPlan(context)
                            .addNode(newOperationVertex(node));
                    context.setVar(OperationVertex.class, vertex);

                    // propagate my parent vertex to my children
                    context.setResult(vertex);                
                    break;
                }
                case LEAVE: {
                    // In order to simplify dependency management between operations,
                    // clear indegrees in this OperationVertex
                    // This will make this vertex the ultimate sink in this sub-graph
                    // In order to simplify propagation of the initial root value to the fields,
                    // add disconnected field vertices as dependencies to the root DocumentVertex
                    DocumentVertex documentVertex = context.getVar(DocumentVertex.class);
                    OperationVertex operationVertex = (OperationVertex)context.getResult();
                    operationVertex
                        .adjacencySet()
                        .stream()
                        .filter(this::isFieldVertex)
                        .forEach(v -> { 
                            v.undependsOn(operationVertex, 
                                edge -> toNodeVertex(v).dependsOn(
                                    toNodeVertex(documentVertex), 
                                    (ExecutionPlanContext ctx, Edge<?, ?> e) -> executionPlan.prepareResolve(ctx, edge)
                                ));
                        });

                    break;
                }
            }

            return TraversalControl.CONTINUE;
        }    

        @Override
        public TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
            switch (context.getVar(NodeTraverser.LeaveOrEnter.class)) {
                case ENTER: {                                
                    NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();

                    // set up parameters to collect child fields
                    FieldCollectorParameters collectorParams = FieldCollectorParameters.newParameters()
                            .schema(executionPlan.schema)
                            .objectType((GraphQLObjectType)parentVertex.getType())
                            .fragments(executionPlan.fragmentsByName)
                            .variables(executionPlan.variables)
                            .build();
                    context.setVar(FieldCollectorParameters.class, collectorParams);
                }
            }

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
            switch (context.getVar(NodeTraverser.LeaveOrEnter.class)) {
                case ENTER: {
                    if (!FIELD_COLLECTOR.shouldCollectInlineFragment(this, node, context.getVar(FieldCollectorParameters.class)))
                        return TraversalControl.ABORT;
                }
            }

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
            switch (context.getVar(NodeTraverser.LeaveOrEnter.class)) {
                case ENTER: {
                    if (!FIELD_COLLECTOR.shouldCollectFragmentSpread(this, node, context.getVar(FieldCollectorParameters.class))) 
                        return TraversalControl.ABORT;
                }
            }

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitField(Field node, TraverserContext<Node> context) {
            switch (context.getVar(NodeTraverser.LeaveOrEnter.class)) {
                case ENTER: {
                    if (!FIELD_COLLECTOR.shouldCollectField(this, node))
                        return TraversalControl.ABORT;

                    // create a vertex for this node and add dependency on the parent one
                    TraverserContext<Node> parentContext = context.getParentContext();
                    NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)parentContext.getResult();

                    FieldVertex vertex = (FieldVertex)this.<FieldVertex>executionPlan(parentContext)
                            .addNode(newFieldVertex(node, (GraphQLObjectType)parentVertex.getType(), parentContext.getVar(FieldVertex.class)));

                    // Note! the ordering of the below dependencies is important:
                    // 1. complete previous resolve
                    // 2. prepare to the next resolve
                    OperationVertex operationVertex = context.getVar(OperationVertex.class);
                    // action in this dependency will be executed when this vertex is resolved
                    toNodeVertex(operationVertex).dependsOn(toNodeVertex(vertex), 
                        (ExecutionPlanContext ctx, Edge<?, ?> e) -> executionPlan.whenResolved(ctx, new NodeEdge(vertex, parentVertex)));
                    
                    // action in this dependency will be executed right after the source had been resolve 
                    // in order to prepare to the next resolve
                    toNodeVertex(vertex).dependsOn(toNodeVertex(parentVertex), executionPlan::prepareResolve);

                    // propagate current scope further to children
                    if (node.getAlias() != null)
                        context.setVar(FieldVertex.class, vertex);

                    // propagate my vertex to my children
                    context.setResult(vertex);
                }                                                           
            }

            return TraversalControl.CONTINUE;
        }

        private static class FieldCollectorHelper extends FieldCollector {
            boolean shouldCollectField (Builder outer, Field node) {
                if (!conditionalNodes.shouldInclude(outer.executionPlan.variables, node.getDirectives()))
                    return false;

                return true;
            }

            boolean shouldCollectInlineFragment (Builder outer, InlineFragment node, FieldCollectorParameters collectorParams) {
                if (!conditionalNodes.shouldInclude(outer.executionPlan.variables, node.getDirectives()))
                    return false;

                if (!doesFragmentConditionMatch(collectorParams, node))
                    return false;

                return true;
            }

            boolean shouldCollectFragmentSpread (Builder outer, FragmentSpread node, FieldCollectorParameters collectorParams) {
                if (!conditionalNodes.shouldInclude(outer.executionPlan.variables, node.getDirectives()))
                    return false;

                FragmentDefinition fragmentDefinition = outer.executionPlan.fragmentsByName.get(node.getName());
                if (!conditionalNodes.shouldInclude(outer.executionPlan.variables, fragmentDefinition.getDirectives()))
                    return false;

                if (!doesFragmentConditionMatch(collectorParams, fragmentDefinition))
                    return false;

                return true;
            }

            private final ConditionalNodes conditionalNodes = new ConditionalNodes();
        }

        private final ExecutionPlan executionPlan;
        private final Collection<OperationDefinition> operations = new ArrayList<>();

        private static final FieldCollectorHelper FIELD_COLLECTOR = new FieldCollectorHelper();
        private static final NodeVertexVisitor<Boolean> IS_FIELD = new NodeVertexVisitor<Boolean>() {
            @Override
            public Boolean visit(FieldVertex node, Boolean data) {
                return true;
            }        
        };        
    }

    private /*final*/ GraphQLSchema schema;
    private /*final*/ Document document;
    private /*final*/ Map<String, OperationDefinition> operationsByName = Collections.emptyMap();
    private /*final*/ Map<String, FragmentDefinition> fragmentsByName = Collections.emptyMap();
    private /*final*/ Map<String, Object> variables = Collections.emptyMap();    
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Builder.class);
}
