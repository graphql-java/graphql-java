package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.Definition;

import static java.lang.String.format;

@Internal
public class NonSDLDefinitionError extends BaseError {

    public NonSDLDefinitionError(Definition definition) {
        super(definition, format("%s The schema definition text contains a non schema definition language (SDL) element '%s'",
                lineCol(definition), definition.getClass().getSimpleName()));
    }
}
