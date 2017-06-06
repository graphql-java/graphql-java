package graphql.schema.idl;

import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;

public class WiringContext {

    private final TypeDefinitionRegistry registry;
    private final TypeDefinition parentType;
    private final FieldDefinition definition;

    public WiringContext(TypeDefinitionRegistry registry, TypeDefinition parentType, FieldDefinition definition) {
        this.registry = registry;
        this.parentType = parentType;
        this.definition = definition;
    }

    public TypeDefinitionRegistry getRegistry() {
        return registry;
    }

    public TypeDefinition getParentType() {
        return parentType;
    }

    public FieldDefinition getDefinition() {
        return definition;
    }
}
