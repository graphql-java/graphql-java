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
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.util.DependencyGraph;
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
        traverser.preOrder(this, operations, TraverserState::newQueueState);
        
        return executionPlan;
    }
    
    private OperationVertex newOperationVertex (OperationDefinition operationDefinition) {
        GraphQLObjectType operationType = Common.getOperationRootType(schema, operationDefinition);
        return new OperationVertex(operationDefinition, operationType);
    }

    private FieldVertex newFieldVertex (Field field, GraphQLObjectType parentType) {
        GraphQLFieldDefinition fieldDefinition = fieldDefinitionHelper.getFieldDef(schema, parentType, field);
        return new FieldVertex(field, fieldDefinition.getType(), parentType);
    }
    
    private <N extends NodeVertex<Node, GraphQLType>> N cast (OperationVertex vertex) {
        return (N)(NodeVertex<? extends Node, ? extends GraphQLType>)vertex;
    }
    
    private <N extends NodeVertex<Node, GraphQLType>> N cast (FieldVertex vertex) {
        return (N)(NodeVertex<? extends Node, ? extends GraphQLType>)vertex;
    }

    private DependencyGraph<? extends NodeVertex<Node, GraphQLType>> executionPlan (TraverserContext<Node> context) {
        return (DependencyGraph<NodeVertex<Node, GraphQLType>>)context.getInitialData();
    }
    
    // NodeVisitor methods
        
    @Override
    public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
        OperationVertex vertex = executionPlan(context)
                .addNode(cast(newOperationVertex(node)))
                .as(OperationVertex.class);      
        
        context.setResult(vertex);
        
        return TraversalControl.CONTINUE;
    }    
    
    @Override
    public TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
        NodeVertex<Node, GraphQLType> vertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();
        
        // set up parameters to collect child fields
        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(schema)
                .objectType((GraphQLObjectType)vertex.getType())
                .fragments(fragmentsByName)
                .variables(variables)
                .build();
        context.setVar(FieldCollectorParameters.class, collectorParameters);
        
        // propagate my parent vertex to my children
        context.setResult(vertex);
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
        if (!conditionalNodes.shouldInclude(variables, node.getDirectives()))
            return TraversalControl.ABORT;
        
        FieldCollectorParameters collectorParameters = context.getParentContext().getVar(FieldCollectorParameters.class);
        if (!fieldCollectorHelper.doesFragmentConditionMatch(collectorParameters, node))
            return TraversalControl.ABORT;
        
        // propagate my parent vertex to my children
        context.setResult(context.getParentResult());
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
        if (!conditionalNodes.shouldInclude(variables, node.getDirectives()))
            return TraversalControl.ABORT;
        
        FragmentDefinition fragmentDefinition = Optional
                .ofNullable(fragmentsByName.get(node.getName()))
                .orElseThrow(() -> new AssertException(String.format("No fragment definition with name '%s'", node.getName())));
        
        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives()))
            return TraversalControl.ABORT;
        
        FieldCollectorParameters collectorParameters = context.getParentContext().getVar(FieldCollectorParameters.class);
        if (!fieldCollectorHelper.doesFragmentConditionMatch(collectorParameters, fragmentDefinition))
            return TraversalControl.ABORT;
        
        // propagate my parent vertex to my children
        context.setResult(context.getParentResult());
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitField(Field node, TraverserContext<Node> context) {
        if (!conditionalNodes.shouldInclude(variables, node.getDirectives()))
            return TraversalControl.ABORT;
        
        // create a vertex for this node and add dependency on the parent one
        NodeVertex<Node, GraphQLType> parentVertex = (NodeVertex<Node, GraphQLType>)context.getParentResult();        
        FieldVertex vertex = executionPlan(context)
                .addNode(cast(newFieldVertex(node, (GraphQLObjectType)parentVertex.getType())))
                .as(FieldVertex.class);
        
        // FIXME: create a real action
        cast(vertex).dependsOn(parentVertex, Edge.emptyAction());
        context.setResult(vertex);
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitVariableDefinition(VariableDefinition node, TraverserContext<Node> context) {
        // FIXME: verify variables here
        return super.visitVariableDefinition(node, context); 
    }
    
    private static class FieldDefinitionHelper extends AsyncSerialExecutionStrategy {
        // make this method accessible from this package
        @Override
        protected GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
            return super.getFieldDef(schema, parentType, field);
        }
    }
    
    private static class FieldCollectorHelper extends FieldCollector {
        // make this method accessible from this package
        @Override
        protected boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, FragmentDefinition fragmentDefinition) {
            return super.doesFragmentConditionMatch(parameters, fragmentDefinition); //To change body of generated methods, choose Tools | Templates.
        }

        // make this method accessible from this package
        @Override
        protected boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, InlineFragment inlineFragment) {
            return super.doesFragmentConditionMatch(parameters, inlineFragment); //To change body of generated methods, choose Tools | Templates.
        }
    }
    
    private /*final*/ GraphQLSchema schema;
    private /*final*/ Document document;
    private /*final*/ Collection<OperationDefinition> operations = new ArrayList<>();
    private /*final*/ Map<String, OperationDefinition> operationsByName = Collections.emptyMap();
    private /*final*/ Map<String, FragmentDefinition> fragmentsByName = Collections.emptyMap();
    private /*final*/ Map<String, Object> variables = Collections.emptyMap();
    
    private static final ConditionalNodes conditionalNodes = new ConditionalNodes();
    private static final FieldDefinitionHelper fieldDefinitionHelper = new FieldDefinitionHelper();
    private static final FieldCollectorHelper fieldCollectorHelper = new FieldCollectorHelper();
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionPlanBuilder.class);
}
