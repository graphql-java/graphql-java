package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;

@PublicApi
public class FieldWiringEnvironment extends WiringEnvironment {

    private final FieldDefinition fieldDefinition;
    private final TypeDefinition parentType;

    FieldWiringEnvironment(TypeDefinitionRegistry registry, TypeDefinition parentType, FieldDefinition fieldDefinition) {
        super(registry);
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
    }

    public FieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public TypeDefinition getParentType() {
        return parentType;
    }
}