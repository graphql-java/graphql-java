package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class InterfaceFieldRedefinitionError extends BaseError {
    public InterfaceFieldRedefinitionError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectFieldType, String interfaceFieldType) {
        super(typeDefinition, format("The %s type '%s' %s has tried to redefine field '%s' defined via interface '%s' %s from '%s' to '%s'",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef), interfaceFieldType, objectFieldType));
    }
}
