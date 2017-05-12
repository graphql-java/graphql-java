package graphql.schema.idl.errors;

import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class NotAnInputTypeError extends BaseError {

    public NotAnInputTypeError(TypeDefinition typeDefinition) {
        super(typeDefinition, format("The %s type is used as an InputType, but is not declared as one", typeDefinition.getName()));
    }
}
