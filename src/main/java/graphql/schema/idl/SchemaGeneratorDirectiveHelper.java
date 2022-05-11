package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLAppliedDirective;
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
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlElementParentTree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.map;

/**
 * This contains the helper code that allows {@link graphql.schema.idl.SchemaDirectiveWiring} implementations
 * to be invoked during schema generation.
 */
@SuppressWarnings("DuplicatedCode")
@Internal
public class SchemaGeneratorDirectiveHelper {

    /**
     * This will return true if something in the RuntimeWiring requires a {@link SchemaDirectiveWiring}.  This is to allow
     * a shortcut to decide that that we dont need ANY SchemaDirectiveWiring post processing
     *
     * @param directiveContainer the element that has directives
     * @param typeRegistry       the type registry
     * @param runtimeWiring      the runtime wiring
     * @param <T>                for two
     *
     * @return true if something in the RuntimeWiring requires a {@link SchemaDirectiveWiring}
     */
    public static <T extends GraphQLDirectiveContainer> boolean schemaDirectiveWiringIsRequired(T directiveContainer, TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring) {

        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();

        Map<String, SchemaDirectiveWiring> registeredWiring = runtimeWiring.getRegisteredDirectiveWiring();
        List<SchemaDirectiveWiring> otherWiring = runtimeWiring.getDirectiveWiring();
        boolean thereAreSome = !registeredWiring.isEmpty() || !otherWiring.isEmpty();
        if (thereAreSome) {
            return true;
        }

        Parameters params = new Parameters(typeRegistry, runtimeWiring, new HashMap<>(), null);
        SchemaDirectiveWiringEnvironment<T> env = new SchemaDirectiveWiringEnvironmentImpl<>(directiveContainer,
                directiveContainer.getDirectives(),
                directiveContainer.getAppliedDirectives(),
                null,
                params);
        // do they dynamically provide a wiring for this element?
        return wiringFactory.providesSchemaDirectiveWiring(env);
    }

    static class Parameters {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring runtimeWiring;
        private final NodeParentTree<NamedNode<?>> nodeParentTree;
        private final Map<String, Object> context;
        private final GraphQLCodeRegistry.Builder codeRegistry;
        private final GraphqlElementParentTree elementParentTree;
        private final GraphQLFieldsContainer fieldsContainer;
        private final GraphQLFieldDefinition fieldDefinition;

        Parameters(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, Map<String, Object> context, GraphQLCodeRegistry.Builder codeRegistry) {
            this(typeRegistry, runtimeWiring, context, codeRegistry, null, null, null, null);
        }

        Parameters(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, Map<String, Object> context, GraphQLCodeRegistry.Builder codeRegistry, NodeParentTree<NamedNode<?>> nodeParentTree, GraphqlElementParentTree elementParentTree, GraphQLFieldsContainer fieldsContainer, GraphQLFieldDefinition fieldDefinition) {
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

        public NodeParentTree<NamedNode<?>> getNodeParentTree() {
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

        public Parameters newParams(GraphQLFieldsContainer fieldsContainer, NodeParentTree<NamedNode<?>> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, fieldsContainer, fieldDefinition);
        }

        public Parameters newParams(GraphQLFieldDefinition fieldDefinition, GraphQLFieldsContainer fieldsContainer, NodeParentTree<NamedNode<?>> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, fieldsContainer, fieldDefinition);
        }

        public Parameters newParams(NodeParentTree<NamedNode<?>> nodeParentTree, GraphqlElementParentTree elementParentTree) {
            return new Parameters(this.typeRegistry, this.runtimeWiring, this.context, this.codeRegistry, nodeParentTree, elementParentTree, this.fieldsContainer, fieldDefinition);
        }
    }

    private NodeParentTree<NamedNode<?>> buildAstTree(NamedNode<?>... nodes) {
        Deque<NamedNode<?>> nodeStack = new ArrayDeque<>();
        for (NamedNode<?> node : nodes) {
            nodeStack.push(node);
        }
        return new NodeParentTree<>(nodeStack);
    }

    private GraphqlElementParentTree buildRuntimeTree(GraphQLSchemaElement... elements) {
        Deque<GraphQLSchemaElement> nodeStack = new ArrayDeque<>();
        for (GraphQLSchemaElement element : elements) {
            nodeStack.push(element);
        }
        return new GraphqlElementParentTree(nodeStack);
    }

    private List<GraphQLArgument> wireArguments(GraphQLFieldDefinition fieldDefinition, GraphQLFieldsContainer fieldsContainer, NamedNode<?> fieldsContainerNode, Parameters params, GraphQLFieldDefinition field) {
        return map(field.getArguments(), argument -> {

            NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(fieldsContainerNode, field.getDefinition(), argument.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(fieldsContainer, field, argument);

            Parameters argParams = params.newParams(fieldDefinition, fieldsContainer, nodeParentTree, elementParentTree);

            return onArgument(argument, argParams);
        });
    }

    private List<GraphQLFieldDefinition> wireFields(GraphQLFieldsContainer fieldsContainer, NamedNode<?> fieldsContainerNode, Parameters params) {
        return map(fieldsContainer.getFieldDefinitions(), fieldDefinition -> {

            // and for each argument in the fieldDefinition run the wiring for them - and note that they can change
            List<GraphQLArgument> startingArgs = fieldDefinition.getArguments();
            List<GraphQLArgument> newArgs = wireArguments(fieldDefinition, fieldsContainer, fieldsContainerNode, params, fieldDefinition);

            if (isNotTheSameObjects(startingArgs, newArgs)) {
                // they may have changed the arguments to the fieldDefinition so reflect that
                fieldDefinition = fieldDefinition.transform(builder -> builder.clearArguments().arguments(newArgs));
            }

            NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(fieldsContainerNode, fieldDefinition.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(fieldsContainer, fieldDefinition);
            Parameters fieldParams = params.newParams(fieldDefinition, fieldsContainer, nodeParentTree, elementParentTree);

            // now for each fieldDefinition run the new wiring and capture the results
            return onField(fieldDefinition, fieldParams);
        });
    }


    public GraphQLObjectType onObject(GraphQLObjectType objectType, Parameters params) {
        List<GraphQLFieldDefinition> startingFields = objectType.getFieldDefinitions();
        List<GraphQLFieldDefinition> newFields = wireFields(objectType, objectType.getDefinition(), params);

        GraphQLObjectType newObjectType = objectType;
        if (isNotTheSameObjects(startingFields, newFields)) {
            newObjectType = objectType.transform(builder -> builder.clearFields().fields(newFields));
        }
        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(newObjectType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newObjectType);
        Parameters newParams = params.newParams(newObjectType, nodeParentTree, elementParentTree);

        return wireDirectives(params,
                newObjectType,
                newObjectType.getDirectives(),
                newObjectType.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onObject);
    }

    public GraphQLInterfaceType onInterface(GraphQLInterfaceType interfaceType, Parameters params) {
        List<GraphQLFieldDefinition> startingFields = interfaceType.getFieldDefinitions();
        List<GraphQLFieldDefinition> newFields = wireFields(interfaceType, interfaceType.getDefinition(), params);

        GraphQLInterfaceType newInterfaceType = interfaceType;
        if (isNotTheSameObjects(startingFields, newFields)) {
            newInterfaceType = interfaceType.transform(builder -> builder.clearFields().fields(newFields));
        }

        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(newInterfaceType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newInterfaceType);
        Parameters newParams = params.newParams(newInterfaceType, nodeParentTree, elementParentTree);

        return wireDirectives(params,
                newInterfaceType,
                newInterfaceType.getDirectives(),
                newInterfaceType.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onInterface);
    }

    public GraphQLEnumType onEnum(final GraphQLEnumType enumType, Parameters params) {

        List<GraphQLEnumValueDefinition> startingEnumValues = enumType.getValues();
        List<GraphQLEnumValueDefinition> newEnumValues = map(startingEnumValues, enumValueDefinition -> {

            NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(enumType.getDefinition(), enumValueDefinition.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(enumType, enumValueDefinition);
            Parameters fieldParams = params.newParams(nodeParentTree, elementParentTree);

            // now for each field run the new wiring and capture the results
            return onEnumValue(enumValueDefinition, fieldParams);
        });

        GraphQLEnumType newEnumType = enumType;
        if (isNotTheSameObjects(startingEnumValues, newEnumValues)) {
            newEnumType = enumType.transform(builder -> builder.clearValues().values(newEnumValues));
        }

        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(newEnumType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newEnumType);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireDirectives(params,
                newEnumType,
                newEnumType.getDirectives(),
                newEnumType.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onEnum);
    }

    public GraphQLInputObjectType onInputObjectType(GraphQLInputObjectType inputObjectType, Parameters params) {
        List<GraphQLInputObjectField> startingFields = inputObjectType.getFieldDefinitions();
        List<GraphQLInputObjectField> newFields = map(startingFields, inputField -> {

            NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(inputObjectType.getDefinition(), inputField.getDefinition());
            GraphqlElementParentTree elementParentTree = buildRuntimeTree(inputObjectType, inputField);
            Parameters fieldParams = params.newParams(nodeParentTree, elementParentTree);

            // now for each field run the new wiring and capture the results
            return onInputObjectField(inputField, fieldParams);
        });
        GraphQLInputObjectType newInputObjectType = inputObjectType;
        if (isNotTheSameObjects(startingFields, newFields)) {
            newInputObjectType = inputObjectType.transform(builder -> builder.clearFields().fields(newFields));
        }

        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(newInputObjectType.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(newInputObjectType);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireDirectives(params,
                newInputObjectType,
                newInputObjectType.getDirectives(),
                newInputObjectType.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onInputObjectType);
    }


    public GraphQLUnionType onUnion(GraphQLUnionType element, Parameters params) {
        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(element.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(element);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireDirectives(params,
                element,
                element.getDirectives(),
                element.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onUnion);
    }

    public GraphQLScalarType onScalar(GraphQLScalarType element, Parameters params) {
        NodeParentTree<NamedNode<?>> nodeParentTree = buildAstTree(element.getDefinition());
        GraphqlElementParentTree elementParentTree = buildRuntimeTree(element);
        Parameters newParams = params.newParams(nodeParentTree, elementParentTree);

        return wireDirectives(params,
                element,
                element.getDirectives(),
                element.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        newParams),
                SchemaDirectiveWiring::onScalar);
    }

    private GraphQLFieldDefinition onField(GraphQLFieldDefinition fieldDefinition, Parameters params) {
        return wireDirectives(params,
                fieldDefinition,
                fieldDefinition.getDirectives(),
                fieldDefinition.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        params),
                SchemaDirectiveWiring::onField);
    }

    private GraphQLInputObjectField onInputObjectField(GraphQLInputObjectField element, Parameters params) {
        return wireDirectives(params,
                element,
                element.getDirectives(),
                element.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        params),
                SchemaDirectiveWiring::onInputObjectField);
    }

    private GraphQLEnumValueDefinition onEnumValue(GraphQLEnumValueDefinition enumValueDefinition, Parameters params) {
        return wireDirectives(params,
                enumValueDefinition,
                enumValueDefinition.getDirectives(),
                enumValueDefinition.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        params),
                SchemaDirectiveWiring::onEnumValue);
    }

    private GraphQLArgument onArgument(GraphQLArgument argument, Parameters params) {
        return wireDirectives(params,
                argument,
                argument.getDirectives(),
                argument.getAppliedDirectives(),
                (outputElement, directives, appliedDirectives, registeredDirective) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement,
                        directives,
                        appliedDirectives,
                        registeredDirective,
                        params),
                SchemaDirectiveWiring::onArgument);
    }


    //
    // builds a type safe SchemaDirectiveWiringEnvironment
    //
    interface EnvBuilder<T extends GraphQLDirectiveContainer> {
        SchemaDirectiveWiringEnvironment<T> apply(T outputElement, List<GraphQLDirective> allDirectives, List<GraphQLAppliedDirective> allAppliedDirectives, GraphQLDirective registeredDirective);
    }

    //
    // invokes the SchemaDirectiveWiring with the provided environment
    //
    interface EnvInvoker<T extends GraphQLDirectiveContainer> {
        T apply(SchemaDirectiveWiring schemaDirectiveWiring, SchemaDirectiveWiringEnvironment<T> env);
    }

    private <T extends GraphQLDirectiveContainer> T wireDirectives(
            Parameters parameters, T element,
            List<GraphQLDirective> allDirectives,
            List<GraphQLAppliedDirective> allAppliedDirectives,
            EnvBuilder<T> envBuilder,
            EnvInvoker<T> invoker) {

        RuntimeWiring runtimeWiring = parameters.getRuntimeWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        SchemaDirectiveWiring schemaDirectiveWiring;

        SchemaDirectiveWiringEnvironment<T> env;
        T outputObject = element;
        //
        // first the specific named directives
        Map<String, SchemaDirectiveWiring> mapOfWiring = runtimeWiring.getRegisteredDirectiveWiring();
        for (GraphQLDirective directive : allDirectives) {
            schemaDirectiveWiring = mapOfWiring.get(directive.getName());
            if (schemaDirectiveWiring != null) {
                env = envBuilder.apply(outputObject, allDirectives, allAppliedDirectives, directive);
                outputObject = invokeWiring(outputObject, invoker, schemaDirectiveWiring, env);
            }
        }
        //
        // now call any statically added to the runtime
        for (SchemaDirectiveWiring directiveWiring : runtimeWiring.getDirectiveWiring()) {
            env = envBuilder.apply(outputObject, allDirectives, allAppliedDirectives, null);
            outputObject = invokeWiring(outputObject, invoker, directiveWiring, env);
        }
        //
        // wiring factory is last (if present)
        env = envBuilder.apply(outputObject, allDirectives, allAppliedDirectives, null);
        if (wiringFactory.providesSchemaDirectiveWiring(env)) {
            schemaDirectiveWiring = assertNotNull(wiringFactory.getSchemaDirectiveWiring(env), () -> "Your WiringFactory MUST provide a non null SchemaDirectiveWiring");
            outputObject = invokeWiring(outputObject, invoker, schemaDirectiveWiring, env);
        }

        return outputObject;
    }

    private <T extends GraphQLDirectiveContainer> T invokeWiring(T element, EnvInvoker<T> invoker, SchemaDirectiveWiring schemaDirectiveWiring, SchemaDirectiveWiringEnvironment<T> env) {
        T newElement = invoker.apply(schemaDirectiveWiring, env);
        assertNotNull(newElement, () -> "The SchemaDirectiveWiring MUST return a non null return value for element '" + element.getName() + "'");
        return newElement;
    }

    private <T> boolean isNotTheSameObjects(List<T> starting, List<T> ending) {
        if (starting == ending) {
            return false;
        }
        if (ending.size() != starting.size()) {
            return true;
        }
        for (int i = 0; i < starting.size(); i++) {
            T startObj = starting.get(i);
            T endObj = ending.get(i);
            // object equality
            if (!(startObj == endObj)) {
                return true;
            }
        }
        return false;
    }
}
