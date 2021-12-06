package graphql.schema.diffing;

import graphql.schema.*;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.Assert.assertNotNull;

public class SchemaGraphFactory {

    public static final String DUMMY_TYPE_VERTICE = "__DUMMY_TYPE_VERTICE";
    private int counter = 1;

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
        traverser.traverse(roots, new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                if (context.thisNode() instanceof GraphQLObjectType) {
                    newObject((GraphQLObjectType) context.thisNode(), schemaGraph);
                }
                if (context.thisNode() instanceof GraphQLInterfaceType) {
                    newInterface((GraphQLInterfaceType) context.thisNode(), schemaGraph);
                }
                if (context.thisNode() instanceof GraphQLUnionType) {
                    newUnion((GraphQLInterfaceType) context.thisNode(), schemaGraph);
                }
                if (context.thisNode() instanceof GraphQLScalarType) {
                    newScalar((GraphQLScalarType) context.thisNode(), schemaGraph);
                }
                if (context.thisNode() instanceof GraphQLInputObjectType) {
                    newInputObject((GraphQLInputObjectType) context.thisNode(), schemaGraph);
                }
                if (context.thisNode() instanceof GraphQLEnumType) {
                    newEnum((GraphQLEnumType) context.thisNode(), schemaGraph);
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

        ArrayList<Vertex> copyOfVertices = new ArrayList<>(schemaGraph.getVertices());
        for (Vertex vertex : copyOfVertices) {
            if ("Object".equals(vertex.getType())) {
                handleObjectVertex(vertex, schemaGraph, schema);
            }
            if ("Interface".equals(vertex.getType())) {
                handleInterfaceVertex(vertex, schemaGraph, schema);
            }
            if ("Union".equals(vertex.getType())) {
                handleUnion(vertex, schemaGraph, schema);
            }
            if ("InputObject".equals(vertex.getType())) {
                handleInputObject(vertex, schemaGraph, schema);
            }
            if ("AppliedDirective".equals(vertex.getType())) {
                handleAppliedDirective(vertex, schemaGraph, schema);
            }
        }
        return schemaGraph;
    }

    private void handleAppliedDirective(Vertex appliedDirectiveVertex, SchemaGraph schemaGraph, GraphQLSchema graphQLSchema) {
        Vertex directiveVertex = schemaGraph.getDirective(appliedDirectiveVertex.get("name"));
        schemaGraph.addEdge(new Edge(appliedDirectiveVertex, directiveVertex));
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
        Vertex dummyTypeVertex = new Vertex(DUMMY_TYPE_VERTICE, String.valueOf(counter++));
        schemaGraph.addVertex(dummyTypeVertex);
        schemaGraph.addEdge(new Edge(inputFieldVertex, dummyTypeVertex));
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(dummyTypeVertex, typeVertex);
        typeEdge.setLabel(GraphQLTypeUtil.simplePrint(type));
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
        Vertex dummyTypeVertex = new Vertex(DUMMY_TYPE_VERTICE, String.valueOf(counter++));
        schemaGraph.addVertex(dummyTypeVertex);
        schemaGraph.addEdge(new Edge(fieldVertex, dummyTypeVertex));
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(dummyTypeVertex, typeVertex);
        typeEdge.setLabel(GraphQLTypeUtil.simplePrint(type));
        schemaGraph.addEdge(typeEdge);

        for (GraphQLArgument graphQLArgument : fieldDefinition.getArguments()) {
            Vertex argumentVertex = schemaGraph.findTargetVertex(fieldVertex, vertex -> vertex.getType().equals("Argument") &&
                    vertex.get("name").equals(graphQLArgument.getName())).get();
            handleArgument(argumentVertex, graphQLArgument, schemaGraph);
        }
    }

    private void handleArgument(Vertex argumentVertex, GraphQLArgument graphQLArgument, SchemaGraph schemaGraph) {
        GraphQLInputType type = graphQLArgument.getType();
        GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(type);
        Vertex typeVertex = assertNotNull(schemaGraph.getType(graphQLUnmodifiedType.getName()));
        Edge typeEdge = new Edge(argumentVertex, typeVertex);
        typeEdge.setLabel(GraphQLTypeUtil.simplePrint(type));
        schemaGraph.addEdge(typeEdge);
    }

    private void newObject(GraphQLObjectType graphQLObjectType, SchemaGraph schemaGraph) {
        Vertex objectVertex = new Vertex("Object", String.valueOf(counter++));
        objectVertex.add("name", graphQLObjectType.getName());
        objectVertex.add("description", graphQLObjectType.getDescription());
        for (GraphQLFieldDefinition fieldDefinition : graphQLObjectType.getFieldDefinitions()) {
            Vertex newFieldVertex = newField(fieldDefinition, schemaGraph);
            schemaGraph.addVertex(newFieldVertex);
            schemaGraph.addEdge(new Edge(objectVertex, newFieldVertex));
        }
        schemaGraph.addVertex(objectVertex);
        schemaGraph.addType(graphQLObjectType.getName(), objectVertex);
        cratedAppliedDirectives(objectVertex, graphQLObjectType.getDirectives(), schemaGraph);
    }

    private Vertex newField(GraphQLFieldDefinition graphQLFieldDefinition, SchemaGraph schemaGraph) {
        Vertex fieldVertex = new Vertex("Field", String.valueOf(counter++));
        fieldVertex.add("name", graphQLFieldDefinition.getName());
        fieldVertex.add("description", graphQLFieldDefinition.getDescription());
        for (GraphQLArgument argument : graphQLFieldDefinition.getArguments()) {
            Vertex argumentVertex = newArgument(argument, schemaGraph);
            schemaGraph.addVertex(argumentVertex);
            schemaGraph.addEdge(new Edge(fieldVertex, argumentVertex));
        }
        cratedAppliedDirectives(fieldVertex, graphQLFieldDefinition.getDirectives(), schemaGraph);
        return fieldVertex;
    }

    private Vertex newArgument(GraphQLArgument graphQLArgument, SchemaGraph schemaGraph) {
        Vertex vertex = new Vertex("Argument", String.valueOf(counter++));
        vertex.add("name", graphQLArgument.getName());
        vertex.add("description", graphQLArgument.getDescription());
        cratedAppliedDirectives(vertex, graphQLArgument.getDirectives(), schemaGraph);
        return vertex;
    }

    private void newScalar(GraphQLScalarType scalarType, SchemaGraph schemaGraph) {
        Vertex scalarVertex = new Vertex("Scalar", String.valueOf(counter++));
        scalarVertex.add("name", scalarType.getName());
        scalarVertex.add("description", scalarType.getDescription());
        schemaGraph.addVertex(scalarVertex);
        schemaGraph.addType(scalarType.getName(), scalarVertex);
        cratedAppliedDirectives(scalarVertex, scalarType.getDirectives(), schemaGraph);
    }

    private void newInterface(GraphQLInterfaceType interfaceType, SchemaGraph schemaGraph) {
        Vertex interfaceVertex = new Vertex("Interface", String.valueOf(counter++));
        interfaceVertex.add("name", interfaceType.getName());
        interfaceVertex.add("description", interfaceType.getDescription());
        for (GraphQLFieldDefinition fieldDefinition : interfaceType.getFieldDefinitions()) {
            Vertex newFieldVertex = newField(fieldDefinition, schemaGraph);
            schemaGraph.addVertex(newFieldVertex);
            schemaGraph.addEdge(new Edge(interfaceVertex, newFieldVertex));
        }
        schemaGraph.addVertex(interfaceVertex);
        schemaGraph.addType(interfaceType.getName(), interfaceVertex);
        cratedAppliedDirectives(interfaceVertex, interfaceType.getDirectives(), schemaGraph);
    }

    private void newEnum(GraphQLEnumType enumType, SchemaGraph schemaGraph) {
        Vertex enumVertex = new Vertex("Enum", String.valueOf(counter++));
        enumVertex.add("name", enumType.getName());
        enumVertex.add("description", enumType.getDescription());
        for (GraphQLEnumValueDefinition enumValue : enumType.getValues()) {
            Vertex enumValueVertex = new Vertex("EnumValue", String.valueOf(counter++));
            enumValueVertex.add("name", enumValue.getName());
            schemaGraph.addVertex(enumValueVertex);
            schemaGraph.addEdge(new Edge(enumVertex, enumValueVertex));
            cratedAppliedDirectives(enumValueVertex, enumValue.getDirectives(), schemaGraph);
        }
        schemaGraph.addVertex(enumVertex);
        schemaGraph.addType(enumType.getName(), enumVertex);
        cratedAppliedDirectives(enumVertex, enumType.getDirectives(), schemaGraph);
    }

    private void newUnion(GraphQLInterfaceType unionType, SchemaGraph schemaGraph) {
        Vertex unionVertex = new Vertex("Union", String.valueOf(counter++));
        unionVertex.add("name", unionType.getName());
        unionVertex.add("description", unionType.getDescription());
        schemaGraph.addVertex(unionVertex);
        schemaGraph.addType(unionType.getName(), unionVertex);
        cratedAppliedDirectives(unionVertex, unionType.getDirectives(), schemaGraph);
    }

    private void newInputObject(GraphQLInputObjectType inputObject, SchemaGraph schemaGraph) {
        Vertex inputObjectVertex = new Vertex("InputObject", String.valueOf(counter++));
        inputObjectVertex.add("name", inputObject.getName());
        inputObjectVertex.add("description", inputObject.getDescription());
        for (GraphQLInputObjectField inputObjectField : inputObject.getFieldDefinitions()) {
            Vertex newInputField = newInputField(inputObjectField, schemaGraph);
            Edge newEdge = new Edge(inputObjectVertex, newInputField);
            schemaGraph.addEdge(newEdge);
            cratedAppliedDirectives(inputObjectVertex, inputObjectField.getDirectives(), schemaGraph);
        }
        schemaGraph.addVertex(inputObjectVertex);
        schemaGraph.addType(inputObject.getName(), inputObjectVertex);
        cratedAppliedDirectives(inputObjectVertex, inputObject.getDirectives(), schemaGraph);
    }

    private void cratedAppliedDirectives(Vertex from, List<GraphQLDirective> appliedDirectives, SchemaGraph
            schemaGraph) {
        for (GraphQLDirective appliedDirective : appliedDirectives) {
            Vertex appliedDirectiveVertex = new Vertex("AppliedDirective", String.valueOf(counter++));
            appliedDirectiveVertex.add("name", appliedDirective.getName());
            for (GraphQLArgument appliedArgument : appliedDirective.getArguments()) {
                Vertex appliedArgumentVertex = new Vertex("AppliedArgument", String.valueOf(counter++));
                appliedArgumentVertex.add("name", appliedArgument.getName());
                appliedArgumentVertex.add("value", appliedArgument.getArgumentValue());
                schemaGraph.addEdge(new Edge(appliedArgumentVertex, appliedArgumentVertex));
            }
            schemaGraph.addVertex(appliedDirectiveVertex);
            schemaGraph.addEdge(new Edge(from, appliedDirectiveVertex));
        }
    }

    private void newDirective(GraphQLDirective directive, SchemaGraph schemaGraph) {
        Vertex directiveVertex = new Vertex("Directive", String.valueOf(counter++));
        directiveVertex.add("name", directive.getName());
        directiveVertex.add("description", directive.getDescription());
        for (GraphQLArgument argument : directive.getArguments()) {
            Vertex argumentVertex = newArgument(argument, schemaGraph);
            schemaGraph.addVertex(argumentVertex);
            schemaGraph.addEdge(new Edge(directiveVertex, argumentVertex));
        }
        schemaGraph.addDirective(directive.getName(), directiveVertex);
        schemaGraph.addVertex(directiveVertex);
    }

    private Vertex newInputField(GraphQLInputObjectField inputField, SchemaGraph schemaGraph) {
        Vertex vertex = new Vertex("InputField", String.valueOf(counter++));
        vertex.add("name", inputField.getName());
        vertex.add("description", inputField.getDescription());
        cratedAppliedDirectives(vertex, inputField.getDirectives(), schemaGraph);
        return vertex;
    }

}
