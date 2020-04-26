package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class MissingInterfaceFieldArgumentsError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public MissingInterfaceFieldArgumentsError(String typeOfType, TypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef) {
        super(objectTypeDef, format("The %s type '%s' %s field '%s' does not have the same number of arguments as specified via interface '%s' %s",
                typeOfType, objectTypeDef.getName(), lineCol(objectTypeDef), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
