package graphql.schema.idl;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * This combines a number of {@link WiringFactory}s together to act as one.  It asks each one
 * whether it handles a type and delegates to the first one to answer yes.
 */
public class CombinedWiringFactory implements WiringFactory {
    private List<WiringFactory> factories;

    public CombinedWiringFactory(List<WiringFactory> factories) {
        assertNotNull(factories, "You must provide a list of wiring factories");
        this.factories = new ArrayList<>(factories);
    }

    @Override
    public boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesTypeResolver(registry, definition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesTypeResolver(registry, definition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesTypeResolver(registry, definition)) {
                return factory.getTypeResolver(registry, definition);
            }
        }
        return null;
    }

    @Override
    public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesTypeResolver(registry, definition)) {
                return factory.getTypeResolver(registry, definition);
            }
        }
        return null;
    }

    @Override
    public boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesDataFetcher(registry, definition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        for (WiringFactory factory : factories) {
            if (factory.providesDataFetcher(registry, definition)) {
                return factory.getDataFetcher(registry, definition);
            }
        }
        return null;
    }
}
