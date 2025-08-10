package graphql.schema.idl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.SchemaProblem;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableMap.copyOf;

/**
 * A {@link ImmutableTypeDefinitionRegistry} contains an immutable set of type definitions that come from compiling
 * a graphql schema definition file via {@link SchemaParser#parse(String)} and is more performant because it
 * uses {@link ImmutableMap} structures.
 */
@SuppressWarnings("rawtypes")
@PublicApi
@NullMarked
public class ImmutableTypeDefinitionRegistry extends TypeDefinitionRegistry {

    ImmutableTypeDefinitionRegistry(TypeDefinitionRegistry registry) {
        super(
                copyOf(registry.objectTypeExtensions),
                copyOf(registry.interfaceTypeExtensions),
                copyOf(registry.unionTypeExtensions),
                copyOf(registry.enumTypeExtensions),
                copyOf(registry.scalarTypeExtensions),
                copyOf(registry.inputObjectTypeExtensions),
                copyOf(registry.types),
                copyOf(registry.scalars()), // has an extra side effect
                copyOf(registry.directiveDefinitions),
                ImmutableList.copyOf(registry.schemaExtensionDefinitions),
                registry.schema,
                registry.schemaParseOrder
        );
    }


    private UnsupportedOperationException unsupportedOperationException() {
        return new UnsupportedOperationException("The TypeDefinitionRegistry is in read only mode");
    }

    @Override
    public TypeDefinitionRegistry merge(TypeDefinitionRegistry typeRegistry) throws SchemaProblem {
        throw unsupportedOperationException();
    }

    @Override
    public Optional<GraphQLError> addAll(Collection<SDLDefinition> definitions) {
        throw unsupportedOperationException();
    }

    @Override
    public Optional<GraphQLError> add(SDLDefinition definition) {
        throw unsupportedOperationException();
    }

    @Override
    public void remove(SDLDefinition definition) {
        throw unsupportedOperationException();
    }

    @Override
    public void remove(String key, SDLDefinition definition) {
        throw unsupportedOperationException();
    }

    @Override
    public Map<String, TypeDefinition> types() {
        return types;
    }

    @Override
    public Map<String, ScalarTypeDefinition> scalars() {
        return scalarTypes;
    }

    @Override
    public Map<String, List<ObjectTypeExtensionDefinition>> objectTypeExtensions() {
        return objectTypeExtensions;
    }

    @Override
    public Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions() {
        return interfaceTypeExtensions;
    }

    @Override
    public Map<String, List<UnionTypeExtensionDefinition>> unionTypeExtensions() {
        return unionTypeExtensions;
    }

    @Override
    public Map<String, List<EnumTypeExtensionDefinition>> enumTypeExtensions() {
        return enumTypeExtensions;
    }

    @Override
    public Map<String, List<ScalarTypeExtensionDefinition>> scalarTypeExtensions() {
        return scalarTypeExtensions;
    }

    @Override
    public Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions() {
        return inputObjectTypeExtensions;
    }

    @Override
    public List<SchemaExtensionDefinition> getSchemaExtensionDefinitions() {
        return schemaExtensionDefinitions;
    }

    @Override
    public Map<String, DirectiveDefinition> getDirectiveDefinitions() {
        return directiveDefinitions;
    }
}
