package graphql.schema.idl.errors;

import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import java.util.Collection;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class MissingTransitiveInterfaceError extends BaseError {
    public MissingTransitiveInterfaceError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition implementedInterface, Collection<InterfaceTypeDefinition> missingInterfaces) {
        super(typeDefinition, format("The %s type '%s' %s must implement %s because it is implemented by '%s' %s",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), "[" + missingInterfaces.stream().map(InterfaceTypeDefinition::getName).collect(joining()) + "]", implementedInterface.getName(), lineCol(implementedInterface)));
    }
}
