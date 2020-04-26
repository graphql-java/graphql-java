package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class MissingInterfaceFieldError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public MissingInterfaceFieldError(String typeOfType, TypeDefinition objectType, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition interfaceFieldDef) {
        super(objectType, format("The %s type '%s' %s does not have a field '%s' required via interface '%s' %s",
                typeOfType, objectType.getName(), lineCol(objectType), interfaceFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
