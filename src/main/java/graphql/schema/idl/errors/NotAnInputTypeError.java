package graphql.schema.idl.errors;

import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class NotAnInputTypeError extends BaseError {

    public NotAnInputTypeError(TypeDefinition typeDefinition) {
        super(typeDefinition, format("expected InputType, but found %s type %s", typeDefinition.getName(), lineCol(typeDefinition)));
    }
}
