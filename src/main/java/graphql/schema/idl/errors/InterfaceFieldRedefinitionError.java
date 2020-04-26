package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class InterfaceFieldRedefinitionError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public InterfaceFieldRedefinitionError(String typeOfType, TypeDefinition objectType, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectFieldType, String interfaceFieldType) {
        super(objectType, format("The %s type '%s' %s has tried to redefine field '%s' defined via interface '%s' %s from '%s' to '%s'",
                typeOfType, objectType.getName(), lineCol(objectType), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef), interfaceFieldType, objectFieldType));
    }
}
