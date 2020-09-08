package graphql.schema.idl;


import graphql.PublicApi;

@PublicApi
abstract class WiringEnvironment {

    private final TypeDefinitionRegistry registry;

    WiringEnvironment(TypeDefinitionRegistry registry) {
        this.registry = registry;
    }

    public TypeDefinitionRegistry getRegistry() {
        return registry;
    }
}
