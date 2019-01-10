/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.AssertException;
import graphql.execution.AsyncSerialExecutionStrategy;
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
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.DependencyGraph;
import graphql.util.Edge;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
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
            protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
                context.setResult(node.getChildren());
                return TraversalControl.QUIT;
            }

            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                FragmentDefinition fragmentDefinition = Optional
                        .ofNullable(fragmentsByName.get(node.getName()))
                        .orElseThrow(() -> new AssertException(String.format("No fragment definition with name '%s'", node.getName())));
                
                context.setResult(fragmentDefinition.getChildren());
                return TraversalControl.QUIT;
            }            
        });
    }
    
    public DependencyGraph<? extends NodeVertex<?, ?>> build () {  
        Objects.requireNonNull(schema);
        
        // walk Operations ASTs to record dependencies between fields
        DependencyGraph<NodeVertex<Node, GraphQLType>> executionPlan = new DependencyGraph<>();
        NodeTraverser traverser = new NodeTraverser(this::getChildrenOf, executionPlan);
        traverser.depthFirst(this, operations);
        
        return executionPlan;
    }
    
    private OperationVertex newOperationVertex (OperationDefinition operationDefinition) {
        GraphQLObjectType operationType = Common.getOperationRootType(schema, operationDefinition);
        return new OperationVertex(operationDefinition, operationType);
    }

    private FieldVertex newFieldVertex (Field field, GraphQLObjectType parentType) {
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(schema, (GraphQLCompositeType)GraphQLTypeUtil.unwrapNonNull(parentType), field.getName());
        return new FieldVertex(field, fieldDefinition.getType(), parentType);
    }
    
    private static <N extends NodeVertex<Node, GraphQLType>> N cast (OperationVertex vertex) {
        return (N)(NodeVertex<? extends Node, ? extends GraphQLType>)vertex;
    }
    
    private static <N extends NodeVertex<Node, GraphQLType>> N cast (FieldVertex vertex) {
        return (N)(NodeVertex<? extends Node, ? extends GraphQLType>)vertex;
    }

    private static DependencyGraph<? extends NodeVertex<Node, GraphQLType>> executionPlan (TraverserContext<Node> context) {
        return (DependencyGraph<NodeVertex<Node, GraphQLType>>)context.getInitialData();
    }
    
    private static LeaveOrEnter leaveOrEnter (TraverserContext<Node> context) {
        return context.getVar(LeaveOrEnter.class);
    }
    
    private static boolean isFieldVertex (NodeVertex<? extends Node, ? extends GraphQLType> vertex) {
        return vertex.accept(false, IS_FIELD);
    }
    
    // NodeVisitor methods
        
    @Override
    public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER: {
                OperationVertex vertex = executionPlan(context)
                        .addNode(cast(newOperationVertex(node)))
                        .as(OperationVertex.class);      
                context.setVar(OperationVertex.class, vertex);
                
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
                    .filter(ExecutionPlanBuilder::isFieldVertex)
                    .forEach(v -> v.undependsOn(vertex));
                
                break;
            }
        }
        
        return TraversalControl.CONTINUE;
    }    
    
    @Override
    public TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER: {                
                // propagate current OperationVertex further to the next neighbors
                context.setVar(OperationVertex.class, context.getParentContext().getVar(OperationVertex.class));
                
                NodeVertex<Node, GraphQLType> vertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();
                // set up parameters to collect child fields
                FIELD_COLLECTOR.createFieldCollectorParameters(this, (GraphQLObjectType)vertex.getType(), context);

                // propagate my parent vertex to my children
                context.setResult(vertex);
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER: {
                if (!FIELD_COLLECTOR.collectInlineFragment(this, node, context))
                    return TraversalControl.ABORT;

                // propagate current OperationVertex further to the next neighbors
                context.setVar(OperationVertex.class, context.getParentContext().getVar(OperationVertex.class));
                // propagate my parent vertex to my children
                context.setResult(context.getParentResult());
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER: {
                if (!FIELD_COLLECTOR.collectFragmentSpread(this, node, context)) 
                    return TraversalControl.ABORT;
                
                // propagate current OperationVertex further to the next neighbors
                context.setVar(OperationVertex.class, context.getParentContext().getVar(OperationVertex.class));
                // propagate my parent vertex to my children
                context.setResult(context.getParentResult());
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitField(Field node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER: {
                NodeVertex<Node, GraphQLType> vertex = FIELD_COLLECTOR.collectField(this, node, context);
                if (vertex == null)
                    return TraversalControl.ABORT;

                // FIXME: create a real action
                OperationVertex operationVertex = context.getParentContext().getVar(OperationVertex.class);
                cast(operationVertex).dependsOn(vertex, Edge.emptyAction());

                // propagate current OperationVertex further to the children
                context.setVar(OperationVertex.class, operationVertex);
                // propagate my vertex to my children
                context.setResult(vertex);
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitVariableDefinition(VariableDefinition node, TraverserContext<Node> context) {
        switch (leaveOrEnter(context)) {
            case ENTER:
            // FIXME: verify variables here
        }
        
        return TraversalControl.CONTINUE; 
    }
    
    private static class FieldCollectorHelper extends FieldCollector {
        NodeVertex<Node, GraphQLType> collectField (ExecutionPlanBuilder outer, Field node, TraverserContext<Node> context) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return null;

            // create a vertex for this node and add dependency on the parent one
            NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();        
            FieldVertex vertex = executionPlan(context)
                    .addNode(cast(outer.newFieldVertex(node, (GraphQLObjectType)parentVertex.getType())))
                    .as(FieldVertex.class);

            // FIXME: create a real action
            return cast(vertex).dependsOn(parentVertex, Edge.emptyAction());
        }
        
        boolean collectInlineFragment (ExecutionPlanBuilder outer, InlineFragment node, TraverserContext<Node> context) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return false;

            FieldCollectorParameters collectorParameters = fieldCollectorParameters(context);
            if (!FIELD_COLLECTOR.doesFragmentConditionMatch(collectorParameters, node))
                return false;

            return true;
        }
        
        boolean collectFragmentSpread (ExecutionPlanBuilder outer, FragmentSpread node, TraverserContext<Node> context) {
            if (!conditionalNodes.shouldInclude(outer.variables, node.getDirectives()))
                return false;

            FragmentDefinition fragmentDefinition = outer.fragmentsByName.get(node.getName());
            if (!conditionalNodes.shouldInclude(outer.variables, fragmentDefinition.getDirectives()))
                return false;

            FieldCollectorParameters collectorParameters = fieldCollectorParameters(context);
            if (!FIELD_COLLECTOR.doesFragmentConditionMatch(collectorParameters, fragmentDefinition))
                return false;

            return true;
        }

        void createFieldCollectorParameters (ExecutionPlanBuilder outer, GraphQLObjectType type, TraverserContext<Node> context) {
            // set up parameters to collect child fields
            FieldCollectorParameters collectorParams = FieldCollectorParameters.newParameters()
                    .schema(outer.schema)
                    .objectType(type)
                    .fragments(outer.fragmentsByName)
                    .variables(outer.variables)
                    .build();
            context.setVar(FieldCollectorParameters.class, collectorParams);
        }
            
        private FieldCollectorParameters fieldCollectorParameters (TraverserContext<Node> context) {
            return context.getVar(FieldCollectorParameters.class);
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
