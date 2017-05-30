package graphql.schema.idl;

import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.TypeResolver;

import static graphql.Assert.assertNeverCalled;

public class NoopWiringFactory implements WiringFactory {
    @Override
    public boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType) {
        return false;
    }

    @Override
    public boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType) {
        return false;
    }

    @Override
    public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType) {
        return assertNeverCalled();
    }

    @Override
    public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType) {
        return assertNeverCalled();
    }
}
