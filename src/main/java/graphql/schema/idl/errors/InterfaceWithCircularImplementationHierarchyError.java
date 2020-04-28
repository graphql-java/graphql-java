package graphql.schema.idl.errors;

import graphql.language.ImplementingTypeDefinition;

import java.util.Set;

import static java.lang.String.format;

public class InterfaceWithCircularImplementationHierarchyError extends BaseError {
    public InterfaceWithCircularImplementationHierarchyError(String typeOfType, ImplementingTypeDefinition typeDefinition, Set<String> interfacesToImplement) {
        super(typeDefinition, format("The interface hierarchy in %s type '%s' %s results in a circular dependency [%s]",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition),
                typeDefinition.getName() + " -> " + String.join(" -> ", interfacesToImplement)
        ));
    }
}
