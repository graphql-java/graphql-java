package graphql.schema.idl;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
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

    @Override
    public boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        return false;
    }

    @Override
    public DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        return null;
    }
}
