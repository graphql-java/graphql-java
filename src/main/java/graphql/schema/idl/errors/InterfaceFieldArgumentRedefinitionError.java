package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;


public class InterfaceFieldArgumentRedefinitionError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public InterfaceFieldArgumentRedefinitionError(String typeOfType, TypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectArgStr, String interfaceArgStr) {
        super(objectTypeDef, format("The %s type '%s' %s has tried to redefine field '%s' arguments defined via interface '%s' %s from '%s' to '%s",
                typeOfType, objectTypeDef.getName(), lineCol(objectTypeDef), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef), interfaceArgStr, objectArgStr));
    }
}
