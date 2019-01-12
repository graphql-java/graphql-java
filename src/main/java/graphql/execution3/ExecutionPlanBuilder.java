/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.AssertException;
import graphql.execution.ConditionalNodes;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.UnknownOperationException;
import graphql.execution2.Common;
import graphql.introspection.Introspection;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeTraverser.LeaveOrEnter;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.DependencyGraph;
import graphql.util.Edge;
import graphql.util.TraversalControl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import graphql.util.TraverserContext;
import graphql.util.TraverserState;
import graphql.util.TraverserState.StackTraverserState;
import graphql.util.TraverserState.TraverserContextBuilder;
import java.util.Set;

/**
 *
 * @author gkesler
 */
class ExecutionPlanBuilder extends NodeVisitorStub {
    public ExecutionPlanBuilder schema (GraphQLSchema schema) {
        this.schema = Objects.requireNonNull(schema);
        return this;
    }
    
    public ExecutionPlanBuilder document (Document document) {
        this.document = Objects.requireNonNull(document);
        
        // to optimize a bit on searching for operations and fragments,
        // let's re-organize this a little
        // FIXME: re-organize Document node to keep operations and fragments indexed
        this.operationsByName = new HashMap<>();
        this.fragmentsByName = new HashMap<>();
        
        document
            .getDefinitions()
            .forEach(definition -> NodeTraverser.oneVisitWithResult(definition, new NodeVisitorStub() {
                @Override
                public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
                    operationsByName.put(node.getName(), node);
                    return TraversalControl.QUIT;
                }
                
                public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
                    fragmentsByName.put(node.getName(), node);
                    return TraversalControl.QUIT;
                }
            }));
        
        return this;
    }
    
    public ExecutionPlanBuilder operation (String operationName) {
        if (operationName == null && operationsByName.size() > 1)
            throw new UnknownOperationException("Must provide operation name if query contains multiple operations.");
        
        
        OperationDefinition operation = Optional
            .ofNullable(operationsByName.get(operationName))
            .orElseThrow(() -> new UnknownOperationException(String.format("Unknown operation named '%s'.", operationName)));
        operations.add(operation);
        
        return this;
    }
    
    public ExecutionPlanBuilder variables (Map<String, Object> variables) {
        this.variables = Objects.requireNonNull(variables);
        return this;
    }
    
    private List<Node> getChildrenOf (Node node) {
        return NodeTraverser.oneVisitWithResult(node, new NodeVisitorStub() {
            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                FragmentDefinition fragmentDefinition = Optional
                    .ofNullable(fragmentsByName.get(node.getName()))
                    .orElseThrow(() -> new AssertException(String.format("No fragment definition with name '%s'", node.getName())));
                
                return visitNode(fragmentDefinition, context);
            }            
            
            @Override
            protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
                context.setResult(node.getChildren());
                return TraversalControl.QUIT;
            }
        });
    }
    
    public DependencyGraph<? extends NodeVertex<?, ?>> build () {  
        Objects.requireNonNull(schema);

        // fix default operation if wasn't provided
        if (operations.isEmpty())
            operation(null);
        
        // walk Operations ASTs to record dependencies between fields
        DependencyGraph<NodeVertex<Node, GraphQLType>> executionPlan = new DependencyGraph<>();
        NodeTraverser traverser = new NodeTraverser(this::getChildrenOf, executionPlan);
        traverser.depthFirst(this, operations, ExecutionPlanBuilder::newTraverserState);
        
        return executionPlan;
    }
    
    private static StackTraverserState<Node> newTraverserState (Object initialData) {
        return TraverserState.newStackState(initialData, ExecutionPlanBuilder::newTraverserContext);
    }
    
    private static TraverserContext<Node> newTraverserContext (TraverserContextBuilder<Node> builder) {
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
        GraphQLObjectType operationType = Common.getOperationRootType(schema, operationDefinition);
        return new OperationVertex(operationDefinition, operationType);
    }

    private FieldVertex newFieldVertex (Field field, GraphQLObjectType parentType, NodeVertex<? super Node, ? super GraphQLType> scope) {
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, (GraphQLCompositeType)GraphQLTypeUtil.unwrapNonNull(parentType), field.getName());
        return new FieldVertex(field, fieldDefinition.getType(), parentType, scope);
    }

    private DependencyGraph<? extends NodeVertex<? extends Node, ? extends GraphQLType>> executionPlan (TraverserContext<Node> context) {
        return (DependencyGraph<NodeVertex<Node, GraphQLType>>)context.getInitialData();
    }
    
    private boolean isFieldVertex (NodeVertex<? extends Node, ? extends GraphQLType> vertex) {
        return vertex.accept(false, IS_FIELD);
    }

    // NodeVisitor methods
        
    @Override
    public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
        switch (context.getVar(LeaveOrEnter.class)) {
            case ENTER: {
                OperationVertex vertex = (OperationVertex)executionPlan(context)
                        .addNode(newOperationVertex(node).asNodeVertex());
                context.setVar(OperationVertex.class, vertex);
                
                // propagate my parent vertex to my children
                context.setResult(vertex);                
                break;
            }
            case LEAVE: {
                // In order to simplify dependency management between operations,
                // clear indegrees in this OperationVertex
                // This will make this vertex the ultimate sink in this sub-graph
                OperationVertex vertex = (OperationVertex)context.getResult();
                vertex
                    .adjacencySet()
                    .stream()
                    .filter(this::isFieldVertex)
                    .forEach(v -> v.undependsOn(vertex));
                
                break;
            }
        }
        
        return TraversalControl.CONTINUE;
    }    
    
    @Override
    public TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
        switch (context.getVar(LeaveOrEnter.class)) {
            case ENTER: {                                
                NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();
                
                // set up parameters to collect child fields
                FieldCollectorParameters collectorParams = FieldCollectorParameters.newParameters()
                        .schema(schema)
                        .objectType((GraphQLObjectType)parentVertex.getType())
                        .fragments(fragmentsByName)
                        .variables(variables)
                        .build();
                context.setVar(FieldCollectorParameters.class, collectorParams);
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
        switch (context.getVar(LeaveOrEnter.class)) {
            case ENTER: {
                if (!FIELD_COLLECTOR.shouldCollectInlineFragment(this, node, context.getVar(FieldCollectorParameters.class)))
                    return TraversalControl.ABORT;
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
        switch (context.getVar(LeaveOrEnter.class)) {
            case ENTER: {
                if (!FIELD_COLLECTOR.shouldCollectFragmentSpread(this, node, context.getVar(FieldCollectorParameters.class))) 
                    return TraversalControl.ABORT;
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitField(Field node, TraverserContext<Node> context) {
        switch (context.getVar(LeaveOrEnter.class)) {
            case ENTER: {
                if (!FIELD_COLLECTOR.shouldCollectField(this, node))
                    return TraversalControl.ABORT;

                // create a vertex for this node and add dependency on the parent one
                TraverserContext<Node> parentContext = context.getParentContext();
                NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)parentContext.getResult();
                
                FieldVertex vertex = (FieldVertex)executionPlan(parentContext)
                        .addNode(newFieldVertex(node, (GraphQLObjectType)parentVertex.getType(), parentContext.getVar(NodeVertex.class)).asNodeVertex());
                // FIXME: create a real action
                vertex.dependsOn(parentVertex.asNodeVertex(), Edge.emptyAction());
                
                OperationVertex operationVertex = context.getVar(OperationVertex.class);
                // FIXME: create a real action
                operationVertex.dependsOn(vertex.asNodeVertex(), Edge.emptyAction());

                // propagate current scope further to children
                if (node.getAlias() != null)
                    context.setVar(NodeVertex.class, vertex);
                
                // propagate my vertex to my children
                context.setResult(vertex);
            }
        }
        
        return TraversalControl.CONTINUE;
    }
    
    private static class FieldCollectorHelper extends FieldCollector {
        boolean shouldCollectField (ExecutionPlanBuilder outer, Field node) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return false;

            return true;
        }
        
        boolean shouldCollectInlineFragment (ExecutionPlanBuilder outer, InlineFragment node, FieldCollectorParameters collectorParams) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return false;

            if (!doesFragmentConditionMatch(collectorParams, node))
                return false;

            return true;
        }
        
        boolean shouldCollectFragmentSpread (ExecutionPlanBuilder outer, FragmentSpread node, FieldCollectorParameters collectorParams) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return false;

            FragmentDefinition fragmentDefinition = outer.fragmentsByName.get(node.getName());
            if (!conditionalNodes.shouldInclude(outer.variables, fragmentDefinition.getDirectives()))
                return false;

            if (!doesFragmentConditionMatch(collectorParams, fragmentDefinition))
                return false;

            return true;
        }
        
        private final ConditionalNodes conditionalNodes = new ConditionalNodes();
    }
    
    private /*final*/ GraphQLSchema schema;
    private /*final*/ Document document;
    private /*final*/ Collection<OperationDefinition> operations = new ArrayList<>();
    private /*final*/ Map<String, OperationDefinition> operationsByName = Collections.emptyMap();
    private /*final*/ Map<String, FragmentDefinition> fragmentsByName = Collections.emptyMap();
    private /*final*/ Map<String, Object> variables = Collections.emptyMap();    

    private static final FieldCollectorHelper FIELD_COLLECTOR = new FieldCollectorHelper();
    private static final NodeVertexVisitor<Boolean> IS_FIELD = new NodeVertexVisitor<Boolean>() {
        @Override
        public Boolean visit(FieldVertex node, Boolean data) {
            return true;
        }        
    };
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionPlanBuilder.class);
}
