package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class InterfaceFieldArgumentNotOptionalError extends BaseError {
    public InterfaceFieldArgumentNotOptionalError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectArgStr) {
        super(typeDefinition, format("The %s type '%s' %s field '%s' defines an additional non-optional argument '%s' which is not allowed because field is also defined in interface '%s' %s.",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), objectFieldDef.getName(), objectArgStr, interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
