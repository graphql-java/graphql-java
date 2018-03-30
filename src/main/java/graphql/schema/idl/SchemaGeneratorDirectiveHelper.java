package graphql.schema.idl;

import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLUnionType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static graphql.Assert.assertNotNull;

class SchemaGeneratorDirectiveHelper {

    static class Parameters {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring runtimeWiring;
        private final NodeParentTree nodeParentTree;
        private final Map<String, Object> context;

        Parameters(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, NodeParentTree<NamedNode> nodeParentTree, Map<String, Object> context) {
            this.typeRegistry = typeRegistry;
            this.runtimeWiring = runtimeWiring;
            this.nodeParentTree = nodeParentTree;
            this.context = context;
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

        public Map<String, Object> getContext() {
            return context;
        }
    }

    public GraphQLObjectType onObject(GraphQLObjectType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onObject);
    }

    public GraphQLFieldDefinition onField(GraphQLFieldDefinition element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onField);
    }

    public GraphQLInterfaceType onInterface(GraphQLInterfaceType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onInterface);
    }

    public GraphQLUnionType onUnion(GraphQLUnionType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onUnion);
    }

    public GraphQLScalarType onScalar(GraphQLScalarType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onScalar);
    }

    public GraphQLEnumType onEnum(GraphQLEnumType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onEnum);
    }

    public GraphQLEnumValueDefinition onEnumValue(GraphQLEnumValueDefinition element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onEnumValue);
    }

    public GraphQLArgument onArgument(GraphQLArgument element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onArgument);
    }

    public GraphQLInputObjectType onInputObjectType(GraphQLInputObjectType element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onInputObjectType);
    }

    public GraphQLInputObjectField onInputObjectField(GraphQLInputObjectField element, Parameters params) {
        return wireForEachDirective(params, element, element.getDirectives(),
                (outputElement, directive) -> new SchemaDirectiveWiringEnvironmentImpl<>(outputElement, directive, params.getNodeParentTree(), params.getTypeRegistry(), params.getContext()), SchemaDirectiveWiring::onInputObjectField);
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
            SchemaDirectiveWiringEnvironment<T> env = envBuilder.apply(outputObject,directive);
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
