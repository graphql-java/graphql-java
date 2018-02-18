package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.ScalarTypeDefinition;

@PublicApi
public class ScalarWiringEnvironment extends WiringEnvironment {

    private final ScalarTypeDefinition scalarTypeDefinition;

    ScalarWiringEnvironment(TypeDefinitionRegistry registry, ScalarTypeDefinition interfaceTypeDefinition) {
        super(registry);
        this.scalarTypeDefinition = interfaceTypeDefinition;
    }

    public ScalarTypeDefinition getScalarTypeDefinition() {
        return scalarTypeDefinition;
    }
}
