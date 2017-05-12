package graphql.schema.idl.errors;

import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class NotAnOutputTypeError extends BaseError {

    public NotAnOutputTypeError(TypeDefinition typeDefinition) {
        super(typeDefinition, format("The %s type is used as an OutputType, but is not declared as one", typeDefinition.getName()));
    }
}
