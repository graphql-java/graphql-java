package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlElementParentTree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static graphql.Assert.assertNotNull;
import static java.util.stream.Collectors.toList;

@Internal
/**
 * This contains the helper code that allows {@link graphql.schema.idl.SchemaDirectiveWiring} implementations
 * to be invoked during schema generation.
 */
class SchemaGeneratorDirectiveHelper {

    static class Parameters {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring runtimeWiring;
        private final NodeParentTree<NamedNode> nodeParentTree;
        private final Map<String, Object> context;
        private final GraphQLCodeRegistry.Builder codeRegistry;
        private final GraphqlElementParentTree elementParentTree;
        private final GraphQLFieldsContainer fieldsContainer;
        private final GraphQLFieldDefinition fieldDefinition;

        Parameters(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, Map<String, Object> context, GraphQLCodeRegistry.Builder codeRegistry) {
            this(typeRegistry, runtimeWiring, context, codeRegistry, null, null, null, null);
        }

        Parameters(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, Map<String, Object> context, GraphQLCodeRegistry.Builder codeRegistry, NodeParentTree<NamedNode> nodeParentTree, GraphqlElementParentTree elementParentTree, GraphQLFieldsContainer fieldsContainer, GraphQLFieldDefinition fieldDefinition) {
            this.typeRegistry = typeRegistry;
            this.runtimeWiring = runtimeWiring;
            this.nodeParentTree = nodeParentTree;
            this.context = context;
            this.codeRegistry = codeRegistry;
            this.elementParentTree = elementParentTree;
            this.fieldsContainer = fieldsContainer;
            this.fieldDefinition = fieldDefinition;
        }

        public TypeDefinitionRegistry getTypeRegistry() {
            return typeRegistry;
        }

        public RuntimeWiring getRuntimeWiring() {
            return runtimeWiring;
        }

        public NodeParentTree<NamedNode> getNodeParentTree() {
            return nodeParentTree;
        }

        public GraphqlElementParentTree getElementParentTree() {
            return elementParentTree;
        }

        public GraphQLFieldsContainer getFieldsContainer() {
            return fieldsContainer;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public GraphQLCodeRegistry.Builder getCodeRegistry() {
            return codeRegistry;
        }

        public GraphQLFieldDefinition getFieldsDefinition() {
            return fieldDefinition;
        }

        public Parameters newParams(GraphQLFieldsContainer fieldsContainer, NodeParentTree<NamedNode> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, fieldsContainer, fieldDefinition);
        }

        public Parameters newParams(GraphQLFieldDefinition fieldDefinition, GraphQLFieldsContainer fieldsContainer, NodeParentTree<NamedNode> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, fieldsContainer, fieldDefinition);
        }

        public Parameters newParams(NodeParentTree<NamedNode> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, this.fieldsContainer, fieldDefinition);
        }
    }

    private NodeParentTree<NamedNode> buildAstTree(NamedNode... nodes) {
        Deque<NamedNode> nodeStack = new ArrayDeque<>();
        for (NamedNode node : nodes) {
            nodeStack.push(node);
        }
        return new NodeParentTree<>(nodeStack);
    }

    private GraphqlElementParentTree buildRuntimeTree(GraphQLType... elements) {
        Deque<GraphQLType> nodeStack = new ArrayDeque<>();
        for (GraphQLType element : elements) {
            nodeStack.push(element);
        }
        return new GraphqlElementParentTree(nodeStack);
    }

    private List<GraphQLArgument> wireArguments(GraphQLFieldDefinition fieldDefinition, GraphQLFieldsContainer fieldsContainer, NamedNode fieldsContainerNode, Parameters params, GraphQLFieldDefinition field) {
        return field.getArguments().stream().map(argument -> {

            NodeParentTree<NamedNode> nodeParentTree = buildAstTree(fieldsContainerNode, field.getDefinition(), argument.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(fieldsContainer, field, argument);

            Parameters argParams = params.newParams(fieldDefinition, fieldsContainer, nodeParentTree, elementParentTree);

            return onArgument(argument, argParams);
        }).collect(toList());
    }

    private List<GraphQLFieldDefinition> wireFields(GraphQLFieldsContainer fieldsContainer, NamedNode fieldsContainerNode, Parameters params) {
        return fieldsContainer.getFieldDefinitions().stream().map(fieldDefinition -> {

            // and for each argument in the fieldDefinition run the wiring for them - and note that they can change
            List<GraphQLArgument> newArgs = wireArguments(fieldDefinition, fieldsContainer, fieldsContainerNode, params, fieldDefinition);

            // they may have changed the arguments to the fieldDefinition so reflect that
            fieldDefinition = fieldDefinition.transform(builder -> builder.clearArguments().arguments(newArgs));

            NodeParentTree<NamedNode> nodeParentTree = buildAstTree(fieldsContainerNode, fieldDefinition.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(fieldsContainer, fieldDefinition);
            Parameters fieldParams = params.newParams(fieldDefinition, fieldsContainer, nodeParentTree, elementParentTree);

            // now for each fieldDefinition run the new wiring and capture the results
            return onField(fieldDefinition, fieldParams);
        }).collect(toList());
    }


    public GraphQLObjectType onObject(final GraphQLObjectType objectType, Parameters params) {
        List<GraphQLFieldDefinition> newFields = wireFields(objectType, objectType.getDefinition(), params);

        GraphQLObjectType newObjectType = objectType.transform(builder -> builder.clearFields().fields(newFields));

        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(newObjectType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newObjectType);
        Parameters newParams = params.newParams(newObjectType, nodeParentTree, elementParentTree);

        return wireForEachDirective(params, newObjectType, newObjectType.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onObject);
    }

    public GraphQLInterfaceType onInterface(GraphQLInterfaceType interfaceType, Parameters params) {
        List<GraphQLFieldDefinition> newFields = wireFields(interfaceType, interfaceType.getDefinition(), params);

        GraphQLInterfaceType newInterfaceType = interfaceType.transform(builder -> builder.clearFields().fields(newFields));

        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(newInterfaceType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newInterfaceType);
        Parameters newParams = params.newParams(newInterfaceType, nodeParentTree, elementParentTree);

        return wireForEachDirective(params, newInterfaceType, newInterfaceType.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onInterface);
    }

    public GraphQLEnumType onEnum(GraphQLEnumType enumType, Parameters params) {

        List<GraphQLEnumValueDefinition> newEnums = enumType.getValues().stream().map(enumValueDefinition -> {

            NodeParentTree<NamedNode> nodeParentTree = buildAstTree(enumType.getDefinition(), enumValueDefinition.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(enumType, enumValueDefinition);
            Parameters fieldParams = params.newParams(nodeParentTree, elementParentTree);

            // now for each field run the new wiring and capture the results
            return onEnumValue(enumValueDefinition, fieldParams);
        }).collect(toList());

        GraphQLEnumType newEnumType = enumType.transform(builder -> builder.clearValues().values(newEnums));

        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(newEnumType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newEnumType);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireForEachDirective(params, newEnumType, newEnumType.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onEnum);
    }

    public GraphQLInputObjectType onInputObjectType(GraphQLInputObjectType inputObjectType, Parameters params) {
        List<GraphQLInputObjectField> newFields = inputObjectType.getFieldDefinitions().stream().map(inputField -> {

            NodeParentTree<NamedNode> nodeParentTree = buildAstTree(inputObjectType.getDefinition(), inputField.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(inputObjectType, inputField);
            Parameters fieldParams = params.newParams(nodeParentTree, elementParentTree);

            // now for each field run the new wiring and capture the results
            return onInputObjectField(inputField, fieldParams);
        }).collect(toList());

        GraphQLInputObjectType newInputObjectType = inputObjectType.transform(builder -> builder.clearFields().fields(newFields));

        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(newInputObjectType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newInputObjectType);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireForEachDirective(params, newInputObjectType, newInputObjectType.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onInputObjectType);
    }


    public GraphQLUnionType onUnion(GraphQLUnionType element, Parameters params) {
        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(element.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(element);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onUnion);
    }

    public GraphQLScalarType onScalar(GraphQLScalarType element, Parameters params) {
        NodeParentTree<NamedNode> nodeParentTree = buildAstTree(element.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(element);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, newParams), SchemaDirectiveWiring::onScalar);
    }

    private GraphQLFieldDefinition onField(GraphQLFieldDefinition fieldDefinition, Parameters params) {
        return wireForEachDirective(params, fieldDefinition, fieldDefinition.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params), SchemaDirectiveWiring::onField);
    }

    private GraphQLInputObjectField onInputObjectField(GraphQLInputObjectField element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params), SchemaDirectiveWiring::onInputObjectField);
    }

    private GraphQLEnumValueDefinition onEnumValue(GraphQLEnumValueDefinition enumValueDefinition, Parameters params) {
        return wireForEachDirective(params, enumValueDefinition, enumValueDefinition.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params), SchemaDirectiveWiring::onEnumValue);
    }

    private GraphQLArgument onArgument(GraphQLArgument argument, Parameters params) {
        return wireForEachDirective(params, argument, argument.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params), SchemaDirectiveWiring::onArgument);
    }


    //
    // builds a type safe SchemaDirectiveWiringEnvironment
    //
    interface EnvBuilder<T extends GraphQLDirectiveContainer> {
        SchemaDirectiveWiringEnvironment<T> apply(T outputElement, GraphQLDirective directive);
    }

    //
    // invokes the SchemaDirectiveWiring with the provided environment
    //
    interface EnvInvoker<T extends GraphQLDirectiveContainer> {
        T apply(SchemaDirectiveWiring schemaDirectiveWiring, SchemaDirectiveWiringEnvironment<T> env);
    }

    private <T extends GraphQLDirectiveContainer> T wireForEachDirective(
            Parameters parameters, T element, List<GraphQLDirective> directives,
            EnvBuilder<T> envBuilder, EnvInvoker<T> invoker) {
        T outputObject = element;
        for (GraphQLDirective directive : directives) {
            SchemaDirectiveWiringEnvironment<T> env = envBuilder.apply(outputObject, directive);
            Optional<SchemaDirectiveWiring> directiveWiring = discoverWiringProvider(parameters, directive.getName(), env);
            if (directiveWiring.isPresent()) {
                SchemaDirectiveWiring schemaDirectiveWiring = directiveWiring.get();
                T newElement = invoker.apply(schemaDirectiveWiring, env);
                assertNotNull(newElement, "The SchemaDirectiveWiring MUST return a non null return value for element '" + element.getName() + "'");
                outputObject = newElement;
            }
        }
        return outputObject;
    }

    private <T extends GraphQLDirectiveContainer> Optional<SchemaDirectiveWiring> discoverWiringProvider(Parameters parameters, String directiveName, SchemaDirectiveWiringEnvironment<T> env) {
        SchemaDirectiveWiring directiveWiring;
        RuntimeWiring runtimeWiring = parameters.getRuntimeWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        if (wiringFactory.providesSchemaDirectiveWiring(env)) {
            directiveWiring = assertNotNull(wiringFactory.getSchemaDirectiveWiring(env), "You MUST provide a non null SchemaDirectiveWiring");
        } else {
            directiveWiring = runtimeWiring.getDirectiveWiring().get(directiveName);
        }
        return Optional.ofNullable(directiveWiring);
    }
}
