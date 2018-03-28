package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Optional;

public class SchemaGeneratorDirectiveSupport {
    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;

    public SchemaGeneratorDirectiveSupport(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring) {
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
    }

    public GraphQLObjectType onObject(GraphQLObjectType element) {
        String objectName = element.getName();
        return runWiring(element, null, objectName, element.getDirectives(),
                SchemaDirectiveWiringEnvironmentImpl::new, SchemaDirectiveWiring::onObject);
    }

    public GraphQLInterfaceType onInterface(GraphQLInterfaceType element) {
        String objectName = element.getName();
        return runWiring(element, null, objectName, element.getDirectives(),
                SchemaDirectiveWiringEnvironmentImpl::new, SchemaDirectiveWiring::onInterface);
    }

    private <T extends GraphQLDirectiveContainer> T runWiring(T element, Object parentObj, String objectName, List<GraphQLDirective> directives, EnvBuilder<T> envBuilder, EnvInvoker<T> invoker) {
        T outputObject = element;
        for (GraphQLDirective directive : directives) {
            SchemaDirectiveWiringEnvironment<T> env = envBuilder.apply(outputObject, parentObj, typeRegistry, directive);
            Optional<SchemaDirectiveWiring> directiveWiring = discoverWiring(objectName, env);
            if (directiveWiring.isPresent()) {
                outputObject = invoker.apply(directiveWiring.get(), env);
            }
        }
        return outputObject;
    }

    interface EnvBuilder<T extends GraphQLDirectiveContainer> {
        SchemaDirectiveWiringEnvironment<T> apply(T element, Object parentObj, TypeDefinitionRegistry registry, GraphQLDirective directive);
    }

    interface EnvInvoker<T extends GraphQLDirectiveContainer> {
        T apply(SchemaDirectiveWiring schemaDirectiveWiring, SchemaDirectiveWiringEnvironment<T> env);
    }

    private <T extends GraphQLDirectiveContainer> Optional<SchemaDirectiveWiring> discoverWiring(String objectName, SchemaDirectiveWiringEnvironment<T> env) {
        SchemaDirectiveWiring directiveWiring;
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        if (wiringFactory.providesSchemaDirectiveWiring(env)) {
            directiveWiring = wiringFactory.getSchemaDirectiveWiring(env);
        } else {
            directiveWiring = runtimeWiring.getDirectiveWiring().get(objectName);
        }
        return Optional.ofNullable(directiveWiring);
    }
}
