package graphql.schema.idl.errors;

import graphql.language.InterfaceTypeDefinition;
import graphql.language.TypeDefinition;

import java.util.Collection;
import java.util.Set;

import static java.lang.String.format;

public class MissingTransitiveInterfaceError extends BaseError {
    // TODO maybe duplicate this method, one for ObjectTypeDefinition and another for InterfaceTypeDefinition
    public MissingTransitiveInterfaceError(String typeOfType, TypeDefinition objectType, Collection<String> missingInterfaces) {
        super(objectType, format("The %s type '%s' %s does not implement the following transitive interfaces: %s",
                typeOfType, objectType.getName(), lineCol(objectType), missingInterfaces));
    }
}
