package graphql.schema.idl;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
abstract class WiringEnvironment {

    private final TypeDefinitionRegistry registry;

    WiringEnvironment(TypeDefinitionRegistry registry) {
        this.registry = registry;
    }

    public TypeDefinitionRegistry getRegistry() {
        return registry;
    }
}
