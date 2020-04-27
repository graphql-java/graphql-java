package graphql.schema.idl.errors;

import graphql.language.ImplementingTypeDefinition;

import java.util.Collection;

import static java.lang.String.format;

public class MissingTransitiveInterfaceError extends BaseError {
    public MissingTransitiveInterfaceError(String typeOfType, ImplementingTypeDefinition typeDefinition, Collection<String> missingInterfaces) {
        super(typeDefinition, format("The %s type '%s' %s does not implement the following transitive interfaces: %s",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), missingInterfaces));
    }
}
