package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.idl.errors.SchemaRedefinitionError;
import graphql.schema.idl.errors.TypeRedefinitionError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * A {@link TypeDefinitionRegistry} contains the set of type definitions that come from compiling
 * a graphql schema definition file via {@link SchemaParser#parse(String)}
 */
public class TypeDefinitionRegistry {

    private final Map<String, ScalarTypeDefinition> scalarTypes = new LinkedHashMap<>();
    private final Map<String, List<ObjectTypeExtensionDefinition>> typeExtensions = new LinkedHashMap<>();
    private final Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions = new LinkedHashMap<>();
    private final Map<String, List<UnionTypeExtensionDefinition>> unionTypeExtensions = new LinkedHashMap<>();
    private final Map<String, List<EnumTypeExtensionDefinition>> enumTypeExtensions = new LinkedHashMap<>();
    private final Map<String, List<ScalarTypeExtensionDefinition>> scalarTypeExtensions = new LinkedHashMap<>();
    private final Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions = new LinkedHashMap<>();
    private final Map<String, TypeDefinition> types = new LinkedHashMap<>();
    private SchemaDefinition schema;

    /**
     * This will merge these type registries together and return this one
     *
     * @param typeRegistry the registry to be merged into this one
     *
     * @return this registry
     *
     * @throws SchemaProblem if there are problems merging the types such as redefinitions
     */
    public TypeDefinitionRegistry merge(TypeDefinitionRegistry typeRegistry) throws SchemaProblem {
        List<GraphQLError> errors = new ArrayList<>();

        Map<String, TypeDefinition> tempTypes = new LinkedHashMap<>();
        typeRegistry.types.values().forEach(newEntry -> {
            Optional<GraphQLError> defined = define(this.types, tempTypes, newEntry);
            defined.ifPresent(errors::add);
        });

        Map<String, ScalarTypeDefinition> tempScalarTypes = new LinkedHashMap<>();
        typeRegistry.scalarTypes.values().forEach(newEntry -> define(this.scalarTypes, tempScalarTypes, newEntry).ifPresent(errors::add));

        if (typeRegistry.schema != null && this.schema != null) {
            errors.add(new SchemaRedefinitionError(this.schema, typeRegistry.schema));
        }

        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        if (this.schema == null) {
            // ensure schema is not overwritten by merge
            this.schema = typeRegistry.schema;
        }

        // ok commit to the merge
        this.types.putAll(tempTypes);
        this.scalarTypes.putAll(tempScalarTypes);
        //
        // merge type extensions since they can be redefined by design
        typeRegistry.typeExtensions.forEach((key, value) -> {
            List<ObjectTypeExtensionDefinition> currentList = this.typeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });
        typeRegistry.interfaceTypeExtensions.forEach((key, value) -> {
            List<InterfaceTypeExtensionDefinition> currentList = this.interfaceTypeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });
        typeRegistry.unionTypeExtensions.forEach((key, value) -> {
            List<UnionTypeExtensionDefinition> currentList = this.unionTypeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });
        typeRegistry.enumTypeExtensions.forEach((key, value) -> {
            List<EnumTypeExtensionDefinition> currentList = this.enumTypeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });
        typeRegistry.scalarTypeExtensions.forEach((key, value) -> {
            List<ScalarTypeExtensionDefinition> currentList = this.scalarTypeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });
        typeRegistry.inputObjectTypeExtensions.forEach((key, value) -> {
            List<InputObjectTypeExtensionDefinition> currentList = this.inputObjectTypeExtensions
                    .computeIfAbsent(key, k -> new ArrayList<>());
            currentList.addAll(value);
        });

        return this;
    }

    /**
     * Adds a definition to the registry
     *
     * @param definition the definition to add
     *
     * @return an optional error
     */
    public Optional<GraphQLError> add(Definition definition) {
        // extensions
        if (definition instanceof ObjectTypeExtensionDefinition) {
            ObjectTypeExtensionDefinition newEntry = (ObjectTypeExtensionDefinition) definition;
            return defineExt(typeExtensions, newEntry, ObjectTypeExtensionDefinition::getName);
        } else if (definition instanceof InterfaceTypeExtensionDefinition) {
            InterfaceTypeExtensionDefinition newEntry = (InterfaceTypeExtensionDefinition) definition;
            return defineExt(interfaceTypeExtensions, newEntry, InterfaceTypeExtensionDefinition::getName);
        } else if (definition instanceof UnionTypeExtensionDefinition) {
            UnionTypeExtensionDefinition newEntry = (UnionTypeExtensionDefinition) definition;
            return defineExt(unionTypeExtensions, newEntry, UnionTypeExtensionDefinition::getName);
        } else if (definition instanceof EnumTypeExtensionDefinition) {
            EnumTypeExtensionDefinition newEntry = (EnumTypeExtensionDefinition) definition;
            return defineExt(enumTypeExtensions, newEntry, EnumTypeExtensionDefinition::getName);
        } else if (definition instanceof ScalarTypeExtensionDefinition) {
            ScalarTypeExtensionDefinition newEntry = (ScalarTypeExtensionDefinition) definition;
            return defineExt(scalarTypeExtensions, newEntry, ScalarTypeExtensionDefinition::getName);
        } else if (definition instanceof InputObjectTypeExtensionDefinition) {
            InputObjectTypeExtensionDefinition newEntry = (InputObjectTypeExtensionDefinition) definition;
            return defineExt(inputObjectTypeExtensions, newEntry, InputObjectTypeExtensionDefinition::getName);
            //
            // normal
        } else if (definition instanceof ScalarTypeDefinition) {
            ScalarTypeDefinition newEntry = (ScalarTypeDefinition) definition;
            return define(scalarTypes, scalarTypes, newEntry);
        } else if (definition instanceof TypeDefinition) {
            TypeDefinition newEntry = (TypeDefinition) definition;
            return define(types, types, newEntry);
        } else if (definition instanceof SchemaDefinition) {
            SchemaDefinition newSchema = (SchemaDefinition) definition;
            if (schema != null) {
                return Optional.of(new SchemaRedefinitionError(this.schema, newSchema));
            } else {
                schema = newSchema;
            }
        }
        return Optional.empty();
    }

    private <T extends TypeDefinition> Optional<GraphQLError> define(Map<String, T> source, Map<String, T> target, T newEntry) {
        String name = newEntry.getName();

        T olderEntry = source.get(name);
        if (olderEntry != null) {
            return Optional.of(handleReDefinition(olderEntry, newEntry));
        } else {
            target.put(name, newEntry);
        }
        return Optional.empty();
    }

    private <T> Optional<GraphQLError> defineExt(Map<String, List<T>> typeExtensions, T newEntry, Function<T, String> namerFunc) {
        List<T> currentList = typeExtensions.computeIfAbsent(namerFunc.apply(newEntry), k -> new ArrayList<>());
        currentList.add(newEntry);
        return Optional.empty();
    }

    public Map<String, TypeDefinition> types() {
        return new LinkedHashMap<>(types);
    }

    public Map<String, ScalarTypeDefinition> scalars() {
        LinkedHashMap<String, ScalarTypeDefinition> scalars = new LinkedHashMap<>(ScalarInfo.STANDARD_SCALAR_DEFINITIONS);
        scalars.putAll(scalarTypes);
        return scalars;
    }

    public Map<String, List<ObjectTypeExtensionDefinition>> objectTypeExtensions() {
        return new LinkedHashMap<>(typeExtensions);
    }

    public Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions() {
        return new LinkedHashMap<>(interfaceTypeExtensions);
    }

    public Map<String, List<UnionTypeExtensionDefinition>> unionTypeExtensions() {
        return new LinkedHashMap<>(unionTypeExtensions);
    }

    public Map<String, List<EnumTypeExtensionDefinition>> enumTypeExtensions() {
        return new LinkedHashMap<>(enumTypeExtensions);
    }

    public Map<String, List<ScalarTypeExtensionDefinition>> scalarTypeExtensions() {
        return new LinkedHashMap<>(scalarTypeExtensions);
    }

    public Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions() {
        return new LinkedHashMap<>(inputObjectTypeExtensions);
    }

    public Optional<SchemaDefinition> schemaDefinition() {
        return ofNullable(schema);
    }

    private GraphQLError handleReDefinition(TypeDefinition oldEntry, TypeDefinition newEntry) {
        return new TypeRedefinitionError(newEntry, oldEntry);
    }

    public boolean hasType(TypeName typeName) {
        String name = typeName.getName();
        return types.containsKey(name) || ScalarInfo.STANDARD_SCALAR_DEFINITIONS.containsKey(name) || scalarTypes.containsKey(name) || typeExtensions.containsKey(name);
    }

    public Optional<TypeDefinition> getType(Type type) {
        String typeName = TypeInfo.typeInfo(type).getName();
        return getType(typeName);
    }

    public <T extends TypeDefinition> Optional<T> getType(Type type, Class<T> ofType) {
        String typeName = TypeInfo.typeInfo(type).getName();
        return getType(typeName, ofType);
    }

    public Optional<TypeDefinition> getType(String typeName) {
        TypeDefinition<?> typeDefinition = types.get(typeName);
        if (typeDefinition != null) {
            return Optional.of(typeDefinition);
        }
        typeDefinition = scalars().get(typeName);
        if (typeDefinition != null) {
            return Optional.of(typeDefinition);
        }
        return Optional.empty();
    }

    public <T extends TypeDefinition> Optional<T> getType(String typeName, Class<T> ofType) {
        Optional<TypeDefinition> type = getType(typeName);
        if (type.isPresent()) {
            TypeDefinition typeDefinition = type.get();
            if (typeDefinition.getClass().equals(ofType)) {
                //noinspection unchecked
                return Optional.of((T) typeDefinition);
            }
        }
        return Optional.empty();
    }
}
