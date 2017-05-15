package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.idl.errors.SchemaRedefinitionError;
import graphql.schema.idl.errors.TypeRedefinitionError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * A {@link TypeDefinitionRegistry} contains the set of type definitions that come from compiling
 * a graphql schema definition file via {@link SchemaParser#parse(String)}
 */
public class TypeDefinitionRegistry {

    private final Map<String, ScalarTypeDefinition> scalarTypes = new LinkedHashMap<>();
    private final Map<String, List<TypeExtensionDefinition>> typeExtensions = new LinkedHashMap<>();
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
        typeRegistry.types.values().forEach(newEntry -> define(this.types, tempTypes, newEntry).ifPresent(errors::add));

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
        typeRegistry.typeExtensions.entrySet().forEach(newEntry -> {
            List<TypeExtensionDefinition> currentList = this.typeExtensions
                    .computeIfAbsent(newEntry.getKey(), k -> new ArrayList<>());
            currentList.addAll(newEntry.getValue());
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
        if (definition instanceof TypeExtensionDefinition) {
            TypeExtensionDefinition newEntry = (TypeExtensionDefinition) definition;
            return defineExt(typeExtensions, newEntry);
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

    private Optional<GraphQLError> defineExt(Map<String, List<TypeExtensionDefinition>> typeExtensions, TypeExtensionDefinition newEntry) {
        List<TypeExtensionDefinition> currentList = typeExtensions.computeIfAbsent(newEntry.getName(), k -> new ArrayList<>());
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

    public Map<String, List<TypeExtensionDefinition>> typeExtensions() {
        return new LinkedHashMap<>(typeExtensions);
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

    public Optional<TypeDefinition> getType(String typeName) {
        TypeDefinition typeDefinition = types.get(typeName);
        if (typeDefinition != null) {
            return Optional.of(typeDefinition);
        }
        typeDefinition = scalars().get(typeName);
        if (typeDefinition != null) {
            return Optional.of(typeDefinition);
        }
        return Optional.empty();
    }
}
