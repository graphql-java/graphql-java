package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class MissingInterfaceFieldArgumentsError extends BaseError {
    public MissingInterfaceFieldArgumentsError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef) {
        super(typeDefinition, format("The %s type '%s' %s field '%s' does not have the same number of arguments as specified via interface '%s' %s",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
