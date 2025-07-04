package graphql.schema.idl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.Type;
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

    private final Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> allImplementationsOf;
    private final Map<InterfaceTypeDefinition, List<ObjectTypeDefinition>> implementationsOf;

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
        allImplementationsOf = calculateAllImplementsOf();
        implementationsOf = calculateImplementationsOf(allImplementationsOf);
    }

    private Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> calculateAllImplementsOf() {
        ImmutableMap.Builder<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> mapBuilder = ImmutableMap.builder();
        List<ImplementingTypeDefinition> implementingTypeDefinitions = getTypes(ImplementingTypeDefinition.class);
        for (TypeDefinition typeDef : types.values()) {
            if (typeDef instanceof InterfaceTypeDefinition) {
                InterfaceTypeDefinition interfaceTypeDef = (InterfaceTypeDefinition) typeDef;
                ImmutableList.Builder<ImplementingTypeDefinition> listBuilder = ImmutableList.builder();
                for (ImplementingTypeDefinition<?> implementingTypeDefinition : implementingTypeDefinitions) {
                    List<Type> implementsList = implementingTypeDefinition.getImplements();
                    for (Type iFace : implementsList) {
                        Optional<InterfaceTypeDefinition> implementsAnInterface = getType(iFace, InterfaceTypeDefinition.class);
                        if (implementsAnInterface.isPresent()) {
                            boolean equals = implementsAnInterface.get().getName().equals(interfaceTypeDef.getName());
                            if (equals) {
                                listBuilder.add(implementingTypeDefinition);
                                break;
                            }
                        }
                    }
                }
                mapBuilder.put(interfaceTypeDef, listBuilder.build());
            }
        }
        return mapBuilder.build();
    }

    private Map<InterfaceTypeDefinition, List<ObjectTypeDefinition>> calculateImplementationsOf(Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> allImplementationsOf1) {
        ImmutableMap.Builder<InterfaceTypeDefinition, List<ObjectTypeDefinition>> mapBuilder = ImmutableMap.builder();
        for (Map.Entry<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> entry : allImplementationsOf1.entrySet()) {
            ImmutableList.Builder<ObjectTypeDefinition> listBuilder = ImmutableList.builder();
            for (ImplementingTypeDefinition implementingTypeDefinition : entry.getValue()) {
                if (implementingTypeDefinition instanceof ObjectTypeDefinition) {
                    listBuilder.add((ObjectTypeDefinition) implementingTypeDefinition);
                }
            }
            mapBuilder.put(entry.getKey(), listBuilder.build());
        }
        return mapBuilder.build();
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

    @Override
    public List<ImplementingTypeDefinition> getAllImplementationsOf(InterfaceTypeDefinition targetInterface) {
        return allImplementationsOf.getOrDefault(targetInterface, ImmutableList.of());
    }

    @Override
    public List<ObjectTypeDefinition> getImplementationsOf(InterfaceTypeDefinition targetInterface) {
        return implementationsOf.getOrDefault(targetInterface, ImmutableList.of());
    }
}
