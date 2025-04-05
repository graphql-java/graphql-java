package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.InterfaceTypeDefinition;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public class InterfaceWiringEnvironment extends WiringEnvironment {

    private final InterfaceTypeDefinition interfaceTypeDefinition;

    InterfaceWiringEnvironment(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceTypeDefinition) {
        super(registry);
        this.interfaceTypeDefinition = interfaceTypeDefinition;
    }

    public InterfaceTypeDefinition getInterfaceTypeDefinition() {
        return interfaceTypeDefinition;
    }
}
