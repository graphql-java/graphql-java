package graphql.schema.idl.errors;

import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import java.util.Collection;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class MissingTransitiveInterfaceError extends BaseError {
    public MissingTransitiveInterfaceError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition implementedInterface, InterfaceTypeDefinition missingInterface) {
        super(typeDefinition, format("The %s type '%s' %s must implement '%s' %s because it is implemented by '%s' %s",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), missingInterface.getName(), lineCol(missingInterface), implementedInterface.getName(), lineCol(implementedInterface)));
    }
}
