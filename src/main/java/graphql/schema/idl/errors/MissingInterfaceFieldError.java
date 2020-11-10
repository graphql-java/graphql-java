package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class MissingInterfaceFieldError extends BaseError {
    public MissingInterfaceFieldError(String typeOfType, ImplementingTypeDefinition objectType, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition interfaceFieldDef) {
        super(objectType, format("The %s type '%s' %s does not have a field '%s' required via interface '%s' %s",
                typeOfType, objectType.getName(), lineCol(objectType), interfaceFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
