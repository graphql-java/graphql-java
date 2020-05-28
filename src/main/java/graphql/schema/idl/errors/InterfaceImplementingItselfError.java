package graphql.schema.idl.errors;

import graphql.language.ImplementingTypeDefinition;

import static java.lang.String.format;

public class InterfaceImplementingItselfError extends BaseError {
    public InterfaceImplementingItselfError(String typeOfType, ImplementingTypeDefinition typeDefinition) {
        super(typeDefinition, format("The %s type '%s' %s cannot implement itself",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition)));
    }
}
