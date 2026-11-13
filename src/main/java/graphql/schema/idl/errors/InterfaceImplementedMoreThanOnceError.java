package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;

import static java.lang.String.format;

@Internal
public class InterfaceImplementedMoreThanOnceError extends BaseError {
    public InterfaceImplementedMoreThanOnceError(String typeOfType, ImplementingTypeDefinition typeDefinition, InterfaceTypeDefinition implementedInterface) {
        super(typeDefinition, format("The %s type '%s' %s can only implement '%s' %s once.",
                typeOfType, typeDefinition.getName(), lineCol(typeDefinition), implementedInterface.getName(), lineCol(implementedInterface)));
    }
}
