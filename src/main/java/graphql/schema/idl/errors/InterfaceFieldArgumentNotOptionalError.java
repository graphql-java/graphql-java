package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class InterfaceFieldArgumentNotOptionalError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public InterfaceFieldArgumentNotOptionalError(String typeOfType, TypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectArgStr) {
        super(objectTypeDef, format("The %s type '%s' %s field '%s' defines an additional non-optional argument '%s' which is not allowed because field is also defined in interface '%s' %s.",
                typeOfType, objectTypeDef.getName(), lineCol(objectTypeDef), objectFieldDef.getName(), objectArgStr, interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
