package graphql.schema.idl;

import graphql.Assert;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.DirectiveRedefinitionError;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.idl.errors.SchemaRedefinitionError;
import graphql.schema.idl.errors.TypeRedefinitionError;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.schema.idl.SchemaExtensionsChecker.defineOperationDefs;
import static graphql.schema.idl.SchemaExtensionsChecker.gatherOperationDefs;
import static graphql.schema.idl.TypeInfo.typeName;
import static java.util.Optional.ofNullable;

/**
 * A {@link TypeDefinitionRegistry} contains the set of type definitions that come from compiling
 * a graphql schema definition file via {@link SchemaParser#parse(String)}
 */
@SuppressWarnings("rawtypes")
@PublicApi
@NullMarked
public class TypeDefinitionRegistry implements Serializable {

    protected final Map<String, List<ObjectTypeExtensionDefinition>> objectTypeExtensions;
    protected final Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions;
    protected final Map<String, List<UnionTypeExtensionDefinition>> unionTypeExtensions;
    protected final Map<String, List<EnumTypeExtensionDefinition>> enumTypeExtensions;
    protected final Map<String, List<ScalarTypeExtensionDefinition>> scalarTypeExtensions;
    protected final Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions;

    protected final Map<String, TypeDefinition> types;
    protected final Map<String, ScalarTypeDefinition> scalarTypes;
    protected final Map<String, DirectiveDefinition> directiveDefinitions;
    protected @Nullable SchemaDefinition schema;
    protected final List<SchemaExtensionDefinition> schemaExtensionDefinitions;
    protected final SchemaParseOrder schemaParseOrder;

    public TypeDefinitionRegistry() {
        objectTypeExtensions = new LinkedHashMap<>();
        interfaceTypeExtensions = new LinkedHashMap<>();
        unionTypeExtensions = new LinkedHashMap<>();
        enumTypeExtensions = new LinkedHashMap<>();
        scalarTypeExtensions = new LinkedHashMap<>();
        inputObjectTypeExtensions = new LinkedHashMap<>();
        types = new LinkedHashMap<>();
        scalarTypes = new LinkedHashMap<>();
        directiveDefinitions = new LinkedHashMap<>();
        schemaExtensionDefinitions = new ArrayList<>();
        schemaParseOrder = new SchemaParseOrder();
    }

    protected TypeDefinitionRegistry(Map<String, List<ObjectTypeExtensionDefinition>> objectTypeExtensions,
                                     Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions,
                                     Map<String, List<UnionTypeExtensionDefinition>> unionTypeExtensions,
                                     Map<String, List<EnumTypeExtensionDefinition>> enumTypeExtensions,
                                     Map<String, List<ScalarTypeExtensionDefinition>> scalarTypeExtensions,
                                     Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions,
                                     Map<String, TypeDefinition> types,
                                     Map<String, ScalarTypeDefinition> scalarTypes,
                                     Map<String, DirectiveDefinition> directiveDefinitions,
                                     List<SchemaExtensionDefinition> schemaExtensionDefinitions,
                                     @Nullable SchemaDefinition schema,
                                     SchemaParseOrder schemaParseOrder) {
        this.objectTypeExtensions = objectTypeExtensions;
        this.interfaceTypeExtensions = interfaceTypeExtensions;
        this.unionTypeExtensions = unionTypeExtensions;
        this.enumTypeExtensions = enumTypeExtensions;
        this.scalarTypeExtensions = scalarTypeExtensions;
        this.inputObjectTypeExtensions = inputObjectTypeExtensions;
        this.types = types;
        this.scalarTypes = scalarTypes;
        this.directiveDefinitions = directiveDefinitions;
        this.schemaExtensionDefinitions = schemaExtensionDefinitions;
        this.schema = schema;
        this.schemaParseOrder = schemaParseOrder;
    }

    /**
     * @return an immutable view of this {@link TypeDefinitionRegistry} that is more performant
     * when in read only mode.
     */
    public ImmutableTypeDefinitionRegistry readOnly() {
        if (this instanceof ImmutableTypeDefinitionRegistry) {
            return (ImmutableTypeDefinitionRegistry) this;
        }
        return new ImmutableTypeDefinitionRegistry(this);
    }

    /**
     * @return the order in which {@link SDLDefinition}s were parsed
     */
    public SchemaParseOrder getParseOrder() {
        return schemaParseOrder;
    }

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

        Map<String, DirectiveDefinition> tempDirectiveDefs = new LinkedHashMap<>();
        typeRegistry.directiveDefinitions.values().forEach(newEntry -> {
            Optional<GraphQLError> defined = define(this.directiveDefinitions, tempDirectiveDefs, newEntry);
            defined.ifPresent(errors::add);
        });

        Map<String, ScalarTypeDefinition> tempScalarTypes = new LinkedHashMap<>();
        typeRegistry.scalarTypes.values().forEach(newEntry -> define(this.scalarTypes, tempScalarTypes, newEntry).ifPresent(errors::add));

        checkMergeSchemaDefs(typeRegistry, errors);

        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        if (this.schema == null) {
            // ensure schema is not overwritten by merge
            this.schema = typeRegistry.schema;
            schemaParseOrder.addDefinition(typeRegistry.schema);
        }
        this.schemaExtensionDefinitions.addAll(typeRegistry.schemaExtensionDefinitions);
        typeRegistry.schemaExtensionDefinitions.forEach(schemaParseOrder::addDefinition);

        // ok commit to the merge
        this.types.putAll(tempTypes);
        this.scalarTypes.putAll(tempScalarTypes);
        this.directiveDefinitions.putAll(tempDirectiveDefs);
        //
        // merge type extensions since they can be redefined by design
        typeRegistry.objectTypeExtensions.forEach((key, value) -> {
            List<ObjectTypeExtensionDefinition> currentList = this.objectTypeExtensions
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

    private Map<String, OperationTypeDefinition> checkMergeSchemaDefs(TypeDefinitionRegistry toBeMergedTypeRegistry, List<GraphQLError> errors) {
        if (toBeMergedTypeRegistry.schema != null && this.schema != null) {
            errors.add(new SchemaRedefinitionError(this.schema, toBeMergedTypeRegistry.schema));
        }

        Map<String, OperationTypeDefinition> tempOperationDefs = gatherOperationDefs(errors, this.schema, this.schemaExtensionDefinitions);
        Map<String, OperationTypeDefinition> mergedOperationDefs = gatherOperationDefs(errors, toBeMergedTypeRegistry.schema, toBeMergedTypeRegistry.schemaExtensionDefinitions);

        defineOperationDefs(errors, mergedOperationDefs.values(), tempOperationDefs);
        return tempOperationDefs;
    }


    private Optional<GraphQLError> checkAddOperationDefs() {
        List<GraphQLError> errors = new ArrayList<>();
        gatherOperationDefs(errors, this.schema, this.schemaExtensionDefinitions);
        if (!errors.isEmpty()) {
            return Optional.of(errors.get(0));
        }
        return Optional.empty();
    }


    /**
     * Adds a a collections of definitions to the registry
     *
     * @param definitions the definitions to add
     *
     * @return an optional error for the first problem, typically type redefinition
     */
    public Optional<GraphQLError> addAll(Collection<SDLDefinition> definitions) {
        for (SDLDefinition definition : definitions) {
            Optional<GraphQLError> error = add(definition);
            if (error.isPresent()) {
                return error;
            }
        }
        return Optional.empty();
    }

    /**
     * Adds a definition to the registry
     *
     * @param definition the definition to add
     *
     * @return an optional error
     */
    public Optional<GraphQLError> add(SDLDefinition definition) {
        // extensions
        if (definition instanceof ObjectTypeExtensionDefinition) {
            ObjectTypeExtensionDefinition newEntry = (ObjectTypeExtensionDefinition) definition;
            return defineExt(objectTypeExtensions, newEntry, ObjectTypeExtensionDefinition::getName);
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
        } else if (definition instanceof SchemaExtensionDefinition) {
            schemaExtensionDefinitions.add((SchemaExtensionDefinition) definition);
            schemaParseOrder.addDefinition(definition);
            Optional<GraphQLError> error = checkAddOperationDefs();
            if (error.isPresent()) {
                return error;
            }
        } else if (definition instanceof ScalarTypeDefinition) {
            ScalarTypeDefinition newEntry = (ScalarTypeDefinition) definition;
            return define(scalarTypes, scalarTypes, newEntry);
        } else if (definition instanceof TypeDefinition) {
            TypeDefinition newEntry = (TypeDefinition) definition;
            return define(types, types, newEntry);
        } else if (definition instanceof DirectiveDefinition) {
            DirectiveDefinition newEntry = (DirectiveDefinition) definition;
            return define(directiveDefinitions, directiveDefinitions, newEntry);
        } else if (definition instanceof SchemaDefinition) {
            SchemaDefinition newSchema = (SchemaDefinition) definition;
            if (schema != null) {
                return Optional.of(new SchemaRedefinitionError(this.schema, newSchema));
            } else {
                schema = newSchema;
                schemaParseOrder.addDefinition(newSchema);
            }
            Optional<GraphQLError> error = checkAddOperationDefs();
            if (error.isPresent()) {
                return error;
            }
        } else {
            return Assert.assertShouldNeverHappen();
        }
        return Optional.empty();
    }

    /**
     * Removes a {@code SDLDefinition} from the definition list.
     *
     * @param definition the definition to remove
     */
    public void remove(SDLDefinition definition) {
        assertNotNull(definition, "definition to remove can't be null");
        schemaParseOrder.removeDefinition(definition);
        if (definition instanceof ObjectTypeExtensionDefinition) {
            removeFromList(objectTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof InterfaceTypeExtensionDefinition) {
            removeFromList(interfaceTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof UnionTypeExtensionDefinition) {
            removeFromList(unionTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof EnumTypeExtensionDefinition) {
            removeFromList(enumTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof ScalarTypeExtensionDefinition) {
            removeFromList(scalarTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof InputObjectTypeExtensionDefinition) {
            removeFromList(inputObjectTypeExtensions, (TypeDefinition) definition);
        } else if (definition instanceof ScalarTypeDefinition) {
            scalarTypes.remove(((ScalarTypeDefinition) definition).getName());
        } else if (definition instanceof TypeDefinition) {
            types.remove(((TypeDefinition) definition).getName());
        } else if (definition instanceof DirectiveDefinition) {
            directiveDefinitions.remove(((DirectiveDefinition) definition).getName());
        } else if (definition instanceof SchemaExtensionDefinition) {
            schemaExtensionDefinitions.remove(definition);
        } else if (definition instanceof SchemaDefinition) {
            schema = null;
        } else {
            Assert.assertShouldNeverHappen();
        }
    }

    private void removeFromList(Map source, TypeDefinition value) {
        //noinspection unchecked
        List<TypeDefinition> list = (List<TypeDefinition>) source.get(value.getName());
        if (list == null) {
            return;
        }
        list.remove(value);
        if (list.isEmpty()) {
            source.remove(value.getName());
        }
    }

    /**
     * Removes a {@code SDLDefinition} from a map.
     *
     * @param key        the key to remove
     * @param definition the definition to remove
     */
    public void remove(String key, SDLDefinition definition) {
        assertNotNull(definition, "definition to remove can't be null");
        assertNotNull(key, "key to remove can't be null");
        schemaParseOrder.removeDefinition(definition);
        if (definition instanceof ObjectTypeExtensionDefinition) {
            removeFromMap(objectTypeExtensions, key);
        } else if (definition instanceof InterfaceTypeExtensionDefinition) {
            removeFromMap(interfaceTypeExtensions, key);
        } else if (definition instanceof UnionTypeExtensionDefinition) {
            removeFromMap(unionTypeExtensions, key);
        } else if (definition instanceof EnumTypeExtensionDefinition) {
            removeFromMap(enumTypeExtensions, key);
        } else if (definition instanceof ScalarTypeExtensionDefinition) {
            removeFromMap(scalarTypeExtensions, key);
        } else if (definition instanceof InputObjectTypeExtensionDefinition) {
            removeFromMap(inputObjectTypeExtensions, key);
        } else if (definition instanceof ScalarTypeDefinition) {
            removeFromMap(scalarTypes, key);
        } else if (definition instanceof TypeDefinition) {
            removeFromMap(types, key);
        } else if (definition instanceof DirectiveDefinition) {
            removeFromMap(directiveDefinitions, key);
        } else if (definition instanceof SchemaExtensionDefinition) {
            schemaExtensionDefinitions.remove(definition);
        } else if (definition instanceof SchemaDefinition) {
            schema = null;
        } else {
            Assert.assertShouldNeverHappen();
        }
    }

    private void removeFromMap(Map source, String key) {
        if (source == null) {
            return;
        }
        source.remove(key);
    }


    private <T extends TypeDefinition> Optional<GraphQLError> define(Map<String, T> source, Map<String, T> target, T newEntry) {
        String name = newEntry.getName();

        T olderEntry = source.get(name);
        if (olderEntry != null) {
            return Optional.of(handleReDefinition(olderEntry, newEntry));
        } else {
            target.put(name, newEntry);
            schemaParseOrder.addDefinition(newEntry);
        }
        return Optional.empty();
    }

    private <T extends DirectiveDefinition> Optional<GraphQLError> define(Map<String, T> source, Map<String, T> target, T newEntry) {
        String name = newEntry.getName();

        T olderEntry = source.get(name);
        if (olderEntry != null) {
            return Optional.of(handleReDefinition(olderEntry, newEntry));
        } else {
            target.put(name, newEntry);
            schemaParseOrder.addDefinition(newEntry);
        }
        return Optional.empty();
    }

    private <T extends TypeDefinition> Optional<GraphQLError> defineExt(Map<String, List<T>> typeExtensions, T newEntry, Function<T, String> namerFunc) {
        List<T> currentList = typeExtensions.computeIfAbsent(namerFunc.apply(newEntry), k -> new ArrayList<>());
        currentList.add(newEntry);
        schemaParseOrder.addDefinition(newEntry);
        return Optional.empty();
    }

    public Map<String, TypeDefinition> types() {
        return new LinkedHashMap<>(types);
    }

    public Map<String, ScalarTypeDefinition> scalars() {
        LinkedHashMap<String, ScalarTypeDefinition> scalars = new LinkedHashMap<>(ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS_DEFINITIONS);
        scalars.putAll(scalarTypes);
        return scalars;
    }

    public Map<String, List<ObjectTypeExtensionDefinition>> objectTypeExtensions() {
        return new LinkedHashMap<>(objectTypeExtensions);
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

    public List<SchemaExtensionDefinition> getSchemaExtensionDefinitions() {
        return new ArrayList<>(schemaExtensionDefinitions);
    }

    private GraphQLError handleReDefinition(TypeDefinition oldEntry, TypeDefinition newEntry) {
        return new TypeRedefinitionError(newEntry, oldEntry);
    }

    private GraphQLError handleReDefinition(DirectiveDefinition oldEntry, DirectiveDefinition newEntry) {
        return new DirectiveRedefinitionError(newEntry, oldEntry);
    }

    public Optional<DirectiveDefinition> getDirectiveDefinition(String directiveName) {
        return Optional.ofNullable(directiveDefinitions.get(directiveName));
    }

    public Map<String, DirectiveDefinition> getDirectiveDefinitions() {
        return new LinkedHashMap<>(directiveDefinitions);
    }

    /**
     * Returns true if the registry has a type of the specified {@link TypeName}
     *
     * @param typeName the type name to check
     *
     * @return true if the registry has a type by that type name
     */
    public boolean hasType(TypeName typeName) {
        String name = typeName.getName();
        return hasType(name);
    }

    /**
     * Returns true if the registry has a type of the specified name
     *
     * @param name the name to check
     *
     * @return true if the registry has a type by that name
     */
    public boolean hasType(String name) {
        return types.containsKey(name) || ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS_DEFINITIONS.containsKey(name) || scalarTypes.containsKey(name) || objectTypeExtensions.containsKey(name);
    }

    /**
     * Returns an optional {@link TypeDefinition} of the specified type or {@link Optional#empty()}
     *
     * @param type the type to check
     *
     * @return an optional {@link TypeDefinition} or empty if it's not found
     *
     * @deprecated use the {@link #getTypeOrNull(Type)} variants instead since they avoid the allocation of an
     * optional object
     */
    @Deprecated(since = "2025-07-7")
    public Optional<TypeDefinition> getType(Type type) {
        return Optional.ofNullable(getTypeOrNull(type));
    }

    /**
     * Returns an optional {@link TypeDefinition} of the specified type with the specified class or {@link Optional#empty()}
     *
     * @param type   the type to check
     * @param ofType the class of {@link TypeDefinition}
     *
     * @return an optional {@link TypeDefinition} or empty if it's not found
     *
     * @deprecated use the {@link #getTypeOrNull(Type)} variants instead since they avoid the allocation of an
     * optional object
     */
    @Deprecated(since = "2025-07-7")
    public <T extends TypeDefinition> Optional<T> getType(Type type, Class<T> ofType) {
        return Optional.ofNullable(getTypeOrNull(typeName(type), ofType));
    }

    /**
     * Returns an optional {@link TypeDefinition} of the specified type name or {@link Optional#empty()}
     *
     * @param typeName the type to check
     *
     * @return an optional {@link TypeDefinition} or empty if it's not found
     *
     * @deprecated use the {@link #getTypeOrNull(Type)} variants instead since they avoid the allocation of an
     * optional object
     */
    @Deprecated(since = "2025-07-7")
    public Optional<TypeDefinition> getType(String typeName) {
        return Optional.ofNullable(getTypeOrNull(typeName));
    }

    /**
     * Returns an optional {@link TypeDefinition} of the specified type name with the specified class or {@link Optional#empty()}
     *
     * @param typeName the type to check
     * @param ofType   the class of {@link TypeDefinition}
     *
     * @deprecated use the {@link #getTypeOrNull(Type)} variants instead since they avoid the allocation of an
     * optional object
     */
    @Deprecated(since = "2025-07-7")
    public <T extends TypeDefinition> Optional<T> getType(String typeName, Class<T> ofType) {
        return Optional.ofNullable(getTypeOrNull(typeName, ofType));
    }

    /**
     * Returns a {@link TypeDefinition} of the specified type or null
     *
     * @param type the type to check
     *
     * @return a {@link TypeDefinition} or null if it's not found
     */
    @Nullable
    public TypeDefinition getTypeOrNull(Type type) {
        return getTypeOrNull(typeName(type));
    }

    /**
     * Returns a {@link TypeDefinition} of the specified type with the specified class or null
     *
     * @param type   the type to check
     * @param ofType the class of {@link TypeDefinition}
     *
     * @return a {@link TypeDefinition} or null if it's not found
     */
    @Nullable
    public <T extends TypeDefinition> T getTypeOrNull(Type type, Class<T> ofType) {
        return getTypeOrNull(typeName(type), ofType);
    }

    /**
     * Returns a {@link TypeDefinition} of the specified name or null
     *
     * @param typeName the type name to check
     *
     * @return a {@link TypeDefinition} or null if it's not found
     */
    @Nullable
    public TypeDefinition getTypeOrNull(String typeName) {
        TypeDefinition<?> typeDefinition = types.get(typeName);
        if (typeDefinition != null) {
            return typeDefinition;
        }
        typeDefinition = scalars().get(typeName);
        return typeDefinition;
    }

    /**
     * Returns a {@link TypeDefinition} of the specified name and class or null
     *
     * @param typeName the type name to check
     * @param ofType   the class of {@link TypeDefinition}
     *
     * @return a {@link TypeDefinition} or null if it's not found
     */
    @Nullable
    public <T extends TypeDefinition> T getTypeOrNull(String typeName, Class<T> ofType) {
        TypeDefinition type = getTypeOrNull(typeName);
        if (type != null) {
            if (type.getClass().equals(ofType)) {
                //noinspection unchecked
                return (T) type;
            }
        }
        return null;
    }

    /**
     * Returns true if the specified type exists in the registry and is an abstract (Interface or Union) type
     *
     * @param type the type to check
     *
     * @return true if its abstract
     */
    public boolean isInterfaceOrUnion(Type type) {
        TypeDefinition typeDefinition = getTypeOrNull(type);
        if (typeDefinition != null) {
            return typeDefinition instanceof UnionTypeDefinition || typeDefinition instanceof InterfaceTypeDefinition;
        }
        return false;
    }

    /**
     * Returns true if the specified type exists in the registry and is an object type or interface
     *
     * @param type the type to check
     *
     * @return true if its an object type or interface
     */
    public boolean isObjectTypeOrInterface(Type type) {
        TypeDefinition typeDefinition = getTypeOrNull(type);
        if (typeDefinition != null) {
            return typeDefinition instanceof ObjectTypeDefinition || typeDefinition instanceof InterfaceTypeDefinition;
        }
        return false;
    }

    /**
     * Returns true if the specified type exists in the registry and is an object type
     *
     * @param type the type to check
     *
     * @return true if its an object type
     */
    public boolean isObjectType(Type type) {
        return getTypeOrNull(type, ObjectTypeDefinition.class) != null;
    }

    /**
     * Returns a list of types in the registry of that specified class
     *
     * @param targetClass the class to search for
     * @param <T>         must extend TypeDefinition
     *
     * @return a list of types of the target class
     */
    public <T extends TypeDefinition> List<T> getTypes(Class<T> targetClass) {
        return ImmutableKit.filterAndMap(types.values(),
                targetClass::isInstance,
                targetClass::cast
        );
    }

    /**
     * Returns a map of types in the registry of that specified class keyed by name
     *
     * @param targetClass the class to search for
     * @param <T>         must extend TypeDefinition
     *
     * @return a map of types
     */
    public <T extends TypeDefinition> Map<String, T> getTypesMap(Class<T> targetClass) {
        List<T> list = getTypes(targetClass);
        return FpKit.getByName(list, TypeDefinition::getName, FpKit.mergeFirst());
    }

    /**
     * Returns the list of object and interface types that implement the given interface type
     *
     * @param targetInterface the target to search for
     *
     * @return the list of object types that implement the given interface type
     *
     * @see TypeDefinitionRegistry#getImplementationsOf(InterfaceTypeDefinition)
     */
    public List<ImplementingTypeDefinition> getAllImplementationsOf(InterfaceTypeDefinition targetInterface) {
        return ImmutableKit.filter(
                getTypes(ImplementingTypeDefinition.class),
                implementingTypeDefinition -> {
                    List<Type<?>> implementsList = implementingTypeDefinition.getImplements();
                    for (Type iFace : implementsList) {
                        InterfaceTypeDefinition interfaceTypeDef = getTypeOrNull(iFace, InterfaceTypeDefinition.class);
                        if (interfaceTypeDef != null) {
                            if (interfaceTypeDef.getName().equals(targetInterface.getName())) {
                                return true;
                            }
                        }
                    }
                    return false;
                });
    }

    /**
     * Returns the list of object interface types that implement the given interface type
     *
     * @param targetInterface the target to search for
     *
     * @return the list of object types that implement the given interface type
     *
     * @see TypeDefinitionRegistry#getAllImplementationsOf(InterfaceTypeDefinition)
     */
    public List<ObjectTypeDefinition> getImplementationsOf(InterfaceTypeDefinition targetInterface) {
        return ImmutableKit.filterAndMap(
                getAllImplementationsOf(targetInterface),
                typeDefinition -> typeDefinition instanceof ObjectTypeDefinition,
                typeDefinition -> (ObjectTypeDefinition) typeDefinition
        );
    }

    /**
     * Returns true of the abstract type is in implemented by the object type or interface
     *
     * @param abstractType the abstract type to check (interface or union)
     * @param possibleType the object type or interface to check
     *
     * @return true if the object type or interface implements the abstract type
     */
    public boolean isPossibleType(Type abstractType, Type possibleType) {
        if (!isInterfaceOrUnion(abstractType)) {
            return false;
        }
        if (!isObjectTypeOrInterface(possibleType)) {
            return false;
        }
        TypeDefinition targetObjectTypeDef = Objects.requireNonNull(getTypeOrNull(possibleType));
        TypeDefinition abstractTypeDef = Objects.requireNonNull(getTypeOrNull(abstractType));
        if (abstractTypeDef instanceof UnionTypeDefinition) {
            List<Type> memberTypes = ((UnionTypeDefinition) abstractTypeDef).getMemberTypes();
            for (Type memberType : memberTypes) {
                ObjectTypeDefinition checkType = getTypeOrNull(memberType, ObjectTypeDefinition.class);
                if (checkType != null) {
                    if (checkType.getName().equals(targetObjectTypeDef.getName())) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            InterfaceTypeDefinition iFace = (InterfaceTypeDefinition) abstractTypeDef;
            for (TypeDefinition<?> t : types.values()) {
                if (t instanceof ImplementingTypeDefinition) {
                    if (t.getName().equals(targetObjectTypeDef.getName())) {
                        ImplementingTypeDefinition<?> itd = (ImplementingTypeDefinition<?>) t;

                        for (Type implementsType : itd.getImplements()) {
                            TypeDefinition<?> matchingInterface = types.get(typeName(implementsType));
                            if (matchingInterface != null && matchingInterface.getName().equals(iFace.getName())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }

    /**
     * Returns true if the maybe type is either equal or a subset of the second super type (covariant).
     *
     * @param maybeSubType the type to check
     * @param superType    the equality checked type
     *
     * @return true if maybeSubType is covariant or equal to superType
     */
    @SuppressWarnings("SimplifiableIfStatement")
    public boolean isSubTypeOf(Type maybeSubType, Type superType) {
        TypeInfo maybeSubTypeInfo = TypeInfo.typeInfo(maybeSubType);
        TypeInfo superTypeInfo = TypeInfo.typeInfo(superType);
        // Equivalent type is a valid subtype
        if (maybeSubTypeInfo.equals(superTypeInfo)) {
            return true;
        }


        // If superType is non-null, maybeSubType must also be non-null.
        if (superTypeInfo.isNonNull()) {
            if (maybeSubTypeInfo.isNonNull()) {
                return isSubTypeOf(maybeSubTypeInfo.unwrapOneType(), superTypeInfo.unwrapOneType());
            }
            return false;
        }
        if (maybeSubTypeInfo.isNonNull()) {
            // If superType is nullable, maybeSubType may be non-null or nullable.
            return isSubTypeOf(maybeSubTypeInfo.unwrapOneType(), superType);
        }

        // If superType type is a list, maybeSubType type must also be a list.
        if (superTypeInfo.isList()) {
            if (maybeSubTypeInfo.isList()) {
                return isSubTypeOf(maybeSubTypeInfo.unwrapOneType(), superTypeInfo.unwrapOneType());
            }
            return false;
        }
        if (maybeSubTypeInfo.isList()) {
            // If superType is not a list, maybeSubType must also be not a list.
            return false;
        }

        // If superType type is an abstract type, maybeSubType type may be a currently
        // possible object type.
        if (isInterfaceOrUnion(superType) &&
                isObjectTypeOrInterface(maybeSubType) &&
                isPossibleType(superType, maybeSubType)) {
            return true;
        }

        // Otherwise, the child type is not a valid subtype of the parent type.
        return false;
    }

}
