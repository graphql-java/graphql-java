package graphql.schema.diffing;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.AstPrinter;
import graphql.schema.*;
import graphql.Directives;
import graphql.schema.idl.ScalarInfo;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;

@Internal
public class SchemaGraphFactory {

    private int counter = 1;
    private final String debugPrefix;

    public SchemaGraphFactory(String debugPrefix) {
        this.debugPrefix = debugPrefix;
    }

    public SchemaGraphFactory() {
        this.debugPrefix = "";
    }

    public SchemaGraph createGraph(GraphQLSchema schema) {
        Set<GraphQLSchemaElement> roots = new LinkedHashSet<>();
        roots.add(schema.getQueryType());
        if (schema.isSupportingMutations()) {
            roots.add(schema.getMutationType());
        }
        if (schema.isSupportingSubscriptions()) {
            roots.add(schema.getSubscriptionType());
        }
        roots.addAll(schema.getAdditionalTypes());
        roots.addAll(schema.getDirectives());
        roots.addAll(schema.getSchemaDirectives());
        roots.add(schema.getIntrospectionSchemaType());
        Traverser<GraphQLSchemaElement> traverser = Traverser.depthFirst(GraphQLSchemaElement::getChildren);
        SchemaGraph schemaGraph = new SchemaGraph();
        class IntrospectionNode {

        }
        traverser.traverse(roots, new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                boolean isIntrospectionNode = false;
                if (context.thisNode() instanceof GraphQLNamedType) {
                    if (Introspection.isIntrospectionTypes((GraphQLNamedType) context.thisNode())) {
                        isIntrospectionNode = true;
                        context.setVar(IntrospectionNode.class, new IntrospectionNode());
                    }
                } else {
                    isIntrospectionNode = context.getVarFromParents(IntrospectionNode.class) != null;
                }
                if (context.thisNode() instanceof GraphQLObjectType) {
                    newObject((GraphQLObjectType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLInterfaceType) {
                    newInterface((GraphQLInterfaceType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLUnionType) {
                    newUnion((GraphQLUnionType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLScalarType) {
                    newScalar((GraphQLScalarType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLInputObjectType) {
                    newInputObject((GraphQLInputObjectType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLEnumType) {
                    newEnum((GraphQLEnumType) context.thisNode(), schemaGraph, isIntrospectionNode);
                }
                if (context.thisNode() instanceof GraphQLDirective) {
                    // only continue if not applied directive
                    if (context.getParentNode() == null) {
                        newDirective((GraphQLDirective) context.thisNode(), schemaGraph);
                    }
                }
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
                return TraversalControl.CONTINUE;
            }
        });
        addSchemaVertex(schemaGraph, schema);

        ArrayList<Vertex> copyOfVertices = new ArrayList<>(schemaGraph.getVertices());
        for (Vertex vertex : copyOfVertices) {
            if (SchemaGraph.OBJECT.equals(vertex.getType())) {
                handleObjectVertex(vertex, schemaGraph, schema);
            }
            if (SchemaGraph.INTERFACE.equals(vertex.getType())) {
                handleInterfaceVertex(vertex, schemaGraph, schema);
            }
            if (SchemaGraph.UNION.equals(vertex.getType())) {
                handleUnion(vertex, schemaGraph, schema);
            }
            if (SchemaGraph.INPUT_OBJECT.equals(vertex.getType())) {
                handleInputObject(vertex, schemaGraph, schema);
            }
            if (SchemaGraph.DIRECTIVE.equals(vertex.getType())) {
                handleDirective(vertex, schemaGraph, schema);
            }
        }
        return schemaGraph;
    }

    private void addSchemaVertex(SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLObjectType queryType = graphQLSchema.getQueryType();
        GraphQLObjectType mutationType = graphQLSchema.getMutationType();
        GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
        Vertex schemaVertex = new Vertex(SchemaGraph.SCHEMA, "schema");
        schemaVertex.add("name", SchemaGraph.SCHEMA);
        schemaGraph.addVertex(schemaVertex);

        Vertex queryVertex = schemaGraph.getType(queryType.getName());
        schemaGraph.addEdge(new Edge(schemaVertex, queryVertex, "query"));
        if (mutationType != null) {
            Vertex mutationVertex = schemaGraph.getType(mutationType.getName());
            schemaGraph.addEdge(new Edge(schemaVertex, mutationVertex, "mutation"));
        }
        if (subscriptionType != null) {
            Vertex subscriptionVertex = schemaGraph.getType(subscriptionType.getName());
            schemaGraph.addEdge(new Edge(schemaVertex, subscriptionVertex, "subscription"));
        }
        createAppliedDirectives(schemaVertex, graphQLSchema.getSchemaDirectives(), schemaGraph);
    }

    private void handleInputObject(Vertex inputObject, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) graphQLSchema.getType(inputObject.get("name"));
        List<GraphQLInputObjectField> inputFields = inputObjectType.getFields();
        for (GraphQLInputObjectField inputField : inputFields) {
            Vertex inputFieldVertex = schemaGraph.findTargetVertex(inputObject, vertex -> vertex.getType().equals("InputField") &&
                    vertex.get("name").equals(inputField.getName())).get();
            handleInputField(inputFieldVertex, inputField, schemaGraph, graphQLSchema);
        }
    }

    private void handleInputField(Vertex inputFieldVertex, GraphQLInputObjectField inputField, SchemaGraph
            schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLInputType type = inputField.getType();
        GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(type);
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(inputFieldVertex, typeVertex);
        String typeEdgeLabel = "type=" + GraphQLTypeUtil.simplePrint(type) + ";defaultValue=";
        if (inputField.hasSetDefaultValue()) {
            typeEdgeLabel += AstPrinter.printAst(ValuesResolver.valueToLiteral(inputField.getInputFieldDefaultValue(), inputField.getType(), GraphQLContext.getDefault(), Locale.getDefault()));
        }

        typeEdge.setLabel(typeEdgeLabel);
        schemaGraph.addEdge(typeEdge);
    }

    private void handleUnion(Vertex unionVertex, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLUnionType unionType = (GraphQLUnionType) graphQLSchema.getType(unionVertex.get("name"));
        List<GraphQLNamedOutputType> types = unionType.getTypes();
        for (GraphQLNamedOutputType unionMemberType : types) {
            Vertex unionMemberVertex = assertNotNull(schemaGraph.getType(unionMemberType.getName()));
            schemaGraph.addEdge(new Edge(unionVertex, unionMemberVertex));
        }
    }

    private void handleInterfaceVertex(Vertex interfaceVertex, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) graphQLSchema.getType(interfaceVertex.get("name"));

        for (GraphQLNamedOutputType implementsInterface : interfaceType.getInterfaces()) {
            Vertex implementsInterfaceVertex = assertNotNull(schemaGraph.getType(implementsInterface.getName()));
            schemaGraph.addEdge(new Edge(interfaceVertex, implementsInterfaceVertex, "implements " + implementsInterface.getName()));
        }

        List<GraphQLFieldDefinition> fieldDefinitions = interfaceType.getFieldDefinitions();
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            Vertex fieldVertex = schemaGraph.findTargetVertex(interfaceVertex, vertex -> vertex.getType().equals("Field") &&
                    vertex.get("name").equals(fieldDefinition.getName())).get();
            handleField(fieldVertex, fieldDefinition, schemaGraph, graphQLSchema);
        }

    }

    private void handleObjectVertex(Vertex objectVertex, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLObjectType objectType = graphQLSchema.getObjectType(objectVertex.get("name"));

        for (GraphQLNamedOutputType implementsInterface : objectType.getInterfaces()) {
            Vertex implementsInterfaceVertex = assertNotNull(schemaGraph.getType(implementsInterface.getName()));
            schemaGraph.addEdge(new Edge(objectVertex, implementsInterfaceVertex, "implements " + implementsInterface.getName()));
        }

        List<GraphQLFieldDefinition> fieldDefinitions = objectType.getFieldDefinitions();
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            Vertex fieldVertex = schemaGraph.findTargetVertex(objectVertex, vertex -> vertex.getType().equals("Field") &&
                    vertex.get("name").equals(fieldDefinition.getName())).get();
            handleField(fieldVertex, fieldDefinition, schemaGraph, graphQLSchema);
        }
    }

    private void handleField(Vertex fieldVertex, GraphQLFieldDefinition fieldDefinition, SchemaGraph
            schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLOutputType type = fieldDefinition.getType();
        GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(type);
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(fieldVertex, typeVertex);
        typeEdge.setLabel("type=" + GraphQLTypeUtil.simplePrint(type) + ";");
        schemaGraph.addEdge(typeEdge);

        for (GraphQLArgument graphQLArgument : fieldDefinition.getArguments()) {
            Vertex argumentVertex = schemaGraph.findTargetVertex(fieldVertex, vertex -> vertex.getType().equals("Argument") &&
                    vertex.get("name").equals(graphQLArgument.getName())).get();
            handleArgument(argumentVertex, graphQLArgument, schemaGraph);
        }
    }

    private void handleDirective(Vertex directive, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        GraphQLDirective graphQLDirective = graphQLSchema.getDirective(directive.getName());
        for (GraphQLArgument graphQLArgument : graphQLDirective.getArguments()) {
            Vertex argumentVertex = schemaGraph.findTargetVertex(directive, vertex -> vertex.isOfType(SchemaGraph.ARGUMENT) &&
                    vertex.getName().equals(graphQLArgument.getName())).get();
            handleArgument(argumentVertex, graphQLArgument, schemaGraph);
        }

    }

    private void handleArgument(Vertex argumentVertex, GraphQLArgument graphQLArgument, SchemaGraph schemaGraph) {
        GraphQLInputType type = graphQLArgument.getType();
        GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(type);
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(argumentVertex, typeVertex);
        String typeEdgeLabel = "type=" + GraphQLTypeUtil.simplePrint(type) + ";defaultValue=";
        if (graphQLArgument.hasSetDefaultValue()) {
            typeEdgeLabel += AstPrinter.printAst(ValuesResolver.valueToLiteral(graphQLArgument.getArgumentDefaultValue(), graphQLArgument.getType(), GraphQLContext.getDefault(), Locale.getDefault()));
        }
        typeEdge.setLabel(typeEdgeLabel);
        schemaGraph.addEdge(typeEdge);
    }

    private void newObject(GraphQLObjectType graphQLObjectType, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex objectVertex = new Vertex(SchemaGraph.OBJECT, debugPrefix + String.valueOf(counter++));
        objectVertex.setBuiltInType(isIntrospectionNode);
        objectVertex.add("name", graphQLObjectType.getName());
        objectVertex.add("description", desc(graphQLObjectType.getDescription()));
        for (GraphQLFieldDefinition fieldDefinition : graphQLObjectType.getFieldDefinitions()) {
            Vertex newFieldVertex = newField(fieldDefinition, schemaGraph, isIntrospectionNode);
            schemaGraph.addVertex(newFieldVertex);
            schemaGraph.addEdge(new Edge(objectVertex, newFieldVertex));
        }
        schemaGraph.addVertex(objectVertex);
        schemaGraph.addType(graphQLObjectType.getName(), objectVertex);
        createAppliedDirectives(objectVertex, graphQLObjectType.getDirectives(), schemaGraph);
    }

    private Vertex newField(GraphQLFieldDefinition graphQLFieldDefinition, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex fieldVertex = new Vertex(SchemaGraph.FIELD, debugPrefix + String.valueOf(counter++));
        fieldVertex.setBuiltInType(isIntrospectionNode);
        fieldVertex.add("name", graphQLFieldDefinition.getName());
        fieldVertex.add("description", desc(graphQLFieldDefinition.getDescription()));
        for (GraphQLArgument argument : graphQLFieldDefinition.getArguments()) {
            Vertex argumentVertex = newArgument(argument, schemaGraph, isIntrospectionNode);
            schemaGraph.addVertex(argumentVertex);
            schemaGraph.addEdge(new Edge(fieldVertex, argumentVertex));
        }
        createAppliedDirectives(fieldVertex, graphQLFieldDefinition.getDirectives(), schemaGraph);
        return fieldVertex;
    }

    private Vertex newArgument(GraphQLArgument graphQLArgument, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex vertex = new Vertex(SchemaGraph.ARGUMENT, debugPrefix + String.valueOf(counter++));
        vertex.setBuiltInType(isIntrospectionNode);
        vertex.add("name", graphQLArgument.getName());
        vertex.add("description", desc(graphQLArgument.getDescription()));
        createAppliedDirectives(vertex, graphQLArgument.getDirectives(), schemaGraph);
        return vertex;
    }

    private void newScalar(GraphQLScalarType scalarType, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex scalarVertex = new Vertex(SchemaGraph.SCALAR, debugPrefix + String.valueOf(counter++));
        scalarVertex.setBuiltInType(isIntrospectionNode);
        if (ScalarInfo.isGraphqlSpecifiedScalar(scalarType.getName())) {
            scalarVertex.setBuiltInType(true);
        }
        scalarVertex.add("name", scalarType.getName());
        scalarVertex.add("description", desc(scalarType.getDescription()));
        scalarVertex.add("specifiedByUrl", scalarType.getSpecifiedByUrl());
        schemaGraph.addVertex(scalarVertex);
        schemaGraph.addType(scalarType.getName(), scalarVertex);
        createAppliedDirectives(scalarVertex, scalarType.getDirectives(), schemaGraph);
    }

    private void newInterface(GraphQLInterfaceType interfaceType, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex interfaceVertex = new Vertex(SchemaGraph.INTERFACE, debugPrefix + String.valueOf(counter++));
        interfaceVertex.setBuiltInType(isIntrospectionNode);
        interfaceVertex.add("name", interfaceType.getName());
        interfaceVertex.add("description", desc(interfaceType.getDescription()));
        for (GraphQLFieldDefinition fieldDefinition : interfaceType.getFieldDefinitions()) {
            Vertex newFieldVertex = newField(fieldDefinition, schemaGraph, isIntrospectionNode);
            schemaGraph.addVertex(newFieldVertex);
            schemaGraph.addEdge(new Edge(interfaceVertex, newFieldVertex));
        }
        schemaGraph.addVertex(interfaceVertex);
        schemaGraph.addType(interfaceType.getName(), interfaceVertex);
        createAppliedDirectives(interfaceVertex, interfaceType.getDirectives(), schemaGraph);
    }

    private void newEnum(GraphQLEnumType enumType, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex enumVertex = new Vertex(SchemaGraph.ENUM, debugPrefix + String.valueOf(counter++));
        enumVertex.setBuiltInType(isIntrospectionNode);
        enumVertex.add("name", enumType.getName());
        enumVertex.add("description", desc(enumType.getDescription()));
        for (GraphQLEnumValueDefinition enumValue : enumType.getValues()) {
            Vertex enumValueVertex = new Vertex(SchemaGraph.ENUM_VALUE, debugPrefix + String.valueOf(counter++));
            enumValueVertex.setBuiltInType(isIntrospectionNode);
            enumValueVertex.add("name", enumValue.getName());
            schemaGraph.addVertex(enumValueVertex);
            schemaGraph.addEdge(new Edge(enumVertex, enumValueVertex));
            createAppliedDirectives(enumValueVertex, enumValue.getDirectives(), schemaGraph);
        }
        schemaGraph.addVertex(enumVertex);
        schemaGraph.addType(enumType.getName(), enumVertex);
        createAppliedDirectives(enumVertex, enumType.getDirectives(), schemaGraph);
    }

    private void newUnion(GraphQLUnionType unionType, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex unionVertex = new Vertex(SchemaGraph.UNION, debugPrefix + String.valueOf(counter++));
        unionVertex.setBuiltInType(isIntrospectionNode);
        unionVertex.add("name", unionType.getName());
        unionVertex.add("description", desc(unionType.getDescription()));
        schemaGraph.addVertex(unionVertex);
        schemaGraph.addType(unionType.getName(), unionVertex);
        createAppliedDirectives(unionVertex, unionType.getDirectives(), schemaGraph);
    }

    private void newInputObject(GraphQLInputObjectType inputObject, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex inputObjectVertex = new Vertex(SchemaGraph.INPUT_OBJECT, debugPrefix + String.valueOf(counter++));
        inputObjectVertex.setBuiltInType(isIntrospectionNode);
        inputObjectVertex.add("name", inputObject.getName());
        inputObjectVertex.add("description", desc(inputObject.getDescription()));
        for (GraphQLInputObjectField inputObjectField : inputObject.getFieldDefinitions()) {
            Vertex newInputField = newInputField(inputObjectField, schemaGraph, isIntrospectionNode);
            Edge newEdge = new Edge(inputObjectVertex, newInputField);
            schemaGraph.addEdge(newEdge);
        }
        schemaGraph.addVertex(inputObjectVertex);
        schemaGraph.addType(inputObject.getName(), inputObjectVertex);
        createAppliedDirectives(inputObjectVertex, inputObject.getDirectives(), schemaGraph);
    }

    private void createAppliedDirectives(Vertex from,
                                         List<GraphQLDirective> appliedDirectives,
                                         SchemaGraph schemaGraph) {
        Map<String, Integer> countByName = new LinkedHashMap<>();
        for (GraphQLDirective appliedDirective : appliedDirectives) {
            Vertex appliedDirectiveVertex = new Vertex(SchemaGraph.APPLIED_DIRECTIVE, debugPrefix + String.valueOf(counter++));
            appliedDirectiveVertex.add("name", appliedDirective.getName());
            for (GraphQLArgument appliedArgument : appliedDirective.getArguments()) {
                if (appliedArgument.hasSetValue()) {
                    Vertex appliedArgumentVertex = new Vertex(SchemaGraph.APPLIED_ARGUMENT, debugPrefix + String.valueOf(counter++));
                    appliedArgumentVertex.add("name", appliedArgument.getName());
                    appliedArgumentVertex.add("value", AstPrinter.printAst(ValuesResolver.valueToLiteral(appliedArgument.getArgumentValue(), appliedArgument.getType(), GraphQLContext.getDefault(), Locale.getDefault())));
                    schemaGraph.addVertex(appliedArgumentVertex);
                    schemaGraph.addEdge(new Edge(appliedDirectiveVertex, appliedArgumentVertex));
                }
            }
            schemaGraph.addVertex(appliedDirectiveVertex);

            // repeatable directives means we can have multiple directives with the same name
            // the edge label indicates the applied directive index
            Integer count = countByName.getOrDefault(appliedDirective.getName(), 0);
            schemaGraph.addEdge(new Edge(from, appliedDirectiveVertex, String.valueOf(count)));
            countByName.put(appliedDirective.getName(), count + 1);
        }
    }

    private void newDirective(GraphQLDirective directive, SchemaGraph schemaGraph) {
        Vertex directiveVertex = new Vertex(SchemaGraph.DIRECTIVE, debugPrefix + String.valueOf(counter++));
        directiveVertex.add("name", directive.getName());
        directiveVertex.add("repeatable", directive.isRepeatable());
        directiveVertex.add("locations", directive.validLocations());
        boolean graphqlSpecified = Directives.isBuiltInDirective(directive.getName());
        directiveVertex.setBuiltInType(graphqlSpecified);
        directiveVertex.add("description", desc(directive.getDescription()));
        for (GraphQLArgument argument : directive.getArguments()) {
            Vertex argumentVertex = newArgument(argument, schemaGraph, graphqlSpecified);
            schemaGraph.addVertex(argumentVertex);
            schemaGraph.addEdge(new Edge(directiveVertex, argumentVertex));
        }
        schemaGraph.addDirective(directive.getName(), directiveVertex);
        schemaGraph.addVertex(directiveVertex);
    }

    private Vertex newInputField(GraphQLInputObjectField inputField, SchemaGraph schemaGraph, boolean isIntrospectionNode) {
        Vertex vertex = new Vertex(SchemaGraph.INPUT_FIELD, debugPrefix + String.valueOf(counter++));
        schemaGraph.addVertex(vertex);
        vertex.setBuiltInType(isIntrospectionNode);
        vertex.add("name", inputField.getName());
        vertex.add("description", desc(inputField.getDescription()));
        createAppliedDirectives(vertex, inputField.getDirectives(), schemaGraph);
        return vertex;
    }

    private String desc(String desc) {
        return desc;
//        return desc != null ? desc.replace("\n", "\\n") : null;
    }

}
