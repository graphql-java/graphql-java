package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

@Internal
public class MissingTypeResolverError extends BaseError {

    public MissingTypeResolverError(TypeDefinition typeDefinition) {
        super(typeDefinition, format("There is no type resolver defined for interface / union '%s' type", typeDefinition.getName()));
    }

}
