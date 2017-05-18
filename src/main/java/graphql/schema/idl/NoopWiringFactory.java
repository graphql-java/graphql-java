package graphql.schema.idl;

import graphql.Assert;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

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
        Assert.assertNeverCalled();
        return null;
    }

    @Override
    public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType) {
        Assert.assertNeverCalled();
        return null;
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
