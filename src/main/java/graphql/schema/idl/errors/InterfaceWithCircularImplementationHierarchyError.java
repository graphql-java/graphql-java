package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class InterfaceWithCircularImplementationHierarchyError extends BaseError {
    public InterfaceWithCircularImplementationHierarchyError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition implementedInterface) {
        super(typeDefinition, format("The %s type '%s' %s cannot implement '%s' %s as this would result in a circular reference",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition),
                implementedInterface.getName(), lineCol(implementedInterface)
        ));
    }
}
