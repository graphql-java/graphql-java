package graphql.schema.universe;

import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.schema.InputValueWithState;
import graphql.schema.idl.ImmutableTypeDefinitionRegistry;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.SchemaTypeChecker;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;

/**
 * Compiles parsed type-system definitions directly into a {@link SUSchema}.
 */
@ExperimentalApi
@NullMarked
public final class SUSchemaGenerator {

    private static final List<DirectiveDefinition> BUILT_IN_DIRECTIVE_DEFINITIONS =
            Collections.unmodifiableList(Arrays.asList(
                    Directives.INCLUDE_DIRECTIVE_DEFINITION,
                    Directives.SKIP_DIRECTIVE_DEFINITION,
                    Directives.DEPRECATED_DIRECTIVE_DEFINITION,
                    Directives.SPECIFIED_BY_DIRECTIVE_DEFINITION,
                    Directives.ONE_OF_DIRECTIVE_DEFINITION,
                    Directives.DEFER_DIRECTIVE_DEFINITION,
                    Directives.EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_DEFINITION));

    private final SchemaUniverse universe;
    private final Map<String, SUNamedType> namedTypes = new LinkedHashMap<>();
    private final Map<String, SUDirective> directiveDefinitions = new LinkedHashMap<>();
    private final Map<String, Map<String, SUArgument>> directiveArguments = new LinkedHashMap<>();
    private final Map<SUArgument, SUType> directiveArgumentTypes = new LinkedHashMap<>();
    private final Set<String> referencedTypeNames = new HashSet<>();
    private @Nullable SUSchemaBuilder schemaBuilder;
    private @Nullable SUObjectType queryType;
    private @Nullable SUObjectType mutationType;
    private @Nullable SUObjectType subscriptionType;

    public SUSchemaGenerator(SchemaUniverse universe) {
        this.universe = assertNotNull(universe);
    }

    public SUSchema generate(String name, TypeDefinitionRegistry registry) {
        assertTrue(schemaBuilder == null, "This schema universe generator has already been used");
        ImmutableTypeDefinitionRegistry definitions = validatedDefinitions(registry);
        schemaBuilder = universe.newSchema(name, schemaDescription(definitions));

        createNamedTypes(definitions);
        createDirectiveDefinitions(definitions);
        expandDirectiveDefinitions(definitions);
        expandNamedTypes(definitions);
        addOperationRoots(definitions);
        addSchemaDirectives(definitions);
        addTypes();
        return builder().build();
    }

    private ImmutableTypeDefinitionRegistry validatedDefinitions(TypeDefinitionRegistry registry) {
        TypeDefinitionRegistry copy = new TypeDefinitionRegistry();
        copy.merge(assertNotNull(registry));
        addBuiltInDirectiveDefinitions(copy);
        ImmutableTypeDefinitionRegistry definitions = copy.readOnly();
        List<GraphQLError> errors =
                new SchemaTypeChecker().checkTypeRegistry(definitions, RuntimeWiring.MOCKED_WIRING);
        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }
        return definitions;
    }

    private void addBuiltInDirectiveDefinitions(TypeDefinitionRegistry registry) {
        for (DirectiveDefinition definition : BUILT_IN_DIRECTIVE_DEFINITIONS) {
            if (registry.getDirectiveDefinition(definition.getName()).isEmpty()) {
                registry.add(definition);
            }
        }
    }

    private void createNamedTypes(ImmutableTypeDefinitionRegistry registry) {
        for (TypeDefinition<?> definition : registry.types().values()) {
            namedTypes.put(definition.getName(), createNamedType(definition));
        }
        for (ScalarTypeDefinition definition : registry.scalars().values()) {
            namedTypes.putIfAbsent(
                    definition.getName(),
                    universe.newScalarType(
                            definition.getName(),
                            description(definition, definition.getDescription())));
        }
    }

    private SUNamedType createNamedType(TypeDefinition<?> definition) {
        if (definition instanceof ObjectTypeDefinition) {
            ObjectTypeDefinition objectType = (ObjectTypeDefinition) definition;
            return universe.newObjectType(
                    objectType.getName(),
                    description(objectType, objectType.getDescription()));
        }
        if (definition instanceof InterfaceTypeDefinition) {
            InterfaceTypeDefinition interfaceType = (InterfaceTypeDefinition) definition;
            return universe.newInterfaceType(
                    interfaceType.getName(),
                    description(interfaceType, interfaceType.getDescription()));
        }
        if (definition instanceof UnionTypeDefinition) {
            UnionTypeDefinition unionType = (UnionTypeDefinition) definition;
            return universe.newUnionType(
                    unionType.getName(),
                    description(unionType, unionType.getDescription()));
        }
        if (definition instanceof EnumTypeDefinition) {
            EnumTypeDefinition enumType = (EnumTypeDefinition) definition;
            return universe.newEnumType(
                    enumType.getName(),
                    description(enumType, enumType.getDescription()));
        }
        if (definition instanceof InputObjectTypeDefinition) {
            InputObjectTypeDefinition inputObjectType = (InputObjectTypeDefinition) definition;
            return universe.newInputObjectType(
                    inputObjectType.getName(),
                    description(inputObjectType, inputObjectType.getDescription()));
        }
        if (definition instanceof ScalarTypeDefinition) {
            ScalarTypeDefinition scalarType = (ScalarTypeDefinition) definition;
            return universe.newScalarType(
                    scalarType.getName(),
                    description(scalarType, scalarType.getDescription()));
        }
        return assertShouldNeverHappen(
                "Unsupported schema universe type definition %s",
                definition.getClass().getName());
    }

    private void createDirectiveDefinitions(ImmutableTypeDefinitionRegistry registry) {
        for (DirectiveDefinition definition : registry.getDirectiveDefinitions().values()) {
            SUDirective directive = universe.newDirective(
                    definition.getName(),
                    description(definition, definition.getDescription()),
                    definition.isRepeatable(),
                    directiveLocations(definition),
                    definition);
            directiveDefinitions.put(definition.getName(), directive);
            builder().addDirectiveDefinition(directive);
        }
    }

    private EnumSet<DirectiveLocation> directiveLocations(DirectiveDefinition definition) {
        EnumSet<DirectiveLocation> result = EnumSet.noneOf(DirectiveLocation.class);
        definition.getDirectiveLocations().forEach(location ->
                result.add(DirectiveLocation.valueOf(
                        location.getName().toUpperCase(Locale.ROOT))));
        return result;
    }

    private void expandDirectiveDefinitions(ImmutableTypeDefinitionRegistry registry) {
        for (DirectiveDefinition definition : registry.getDirectiveDefinitions().values()) {
            SUDirective directive = assertNotNull(directiveDefinitions.get(definition.getName()));
            Map<String, SUArgument> arguments = new LinkedHashMap<>();
            directiveArguments.put(definition.getName(), arguments);
            for (InputValueDefinition argumentDefinition :
                    definition.getInputValueDefinitions()) {
                SUArgument argument = newArgument(argumentDefinition);
                SUType type = typeVertex(argumentDefinition.getType());
                arguments.put(argument.getName(), argument);
                directiveArgumentTypes.put(argument, type);
                builder().addArgument(directive, argument);
                builder().setArgumentType(argument, type);
                addAppliedDirectives(argumentDefinition.getDirectives(), argument);
            }
        }
    }

    private void expandNamedTypes(ImmutableTypeDefinitionRegistry registry) {
        for (TypeDefinition<?> definition : registry.types().values()) {
            SUNamedType type = namedType(definition.getName());
            if (definition instanceof ObjectTypeDefinition) {
                expandObject((ObjectTypeDefinition) definition, (SUObjectType) type, registry);
            } else if (definition instanceof InterfaceTypeDefinition) {
                expandInterface(
                        (InterfaceTypeDefinition) definition,
                        (SUInterfaceType) type,
                        registry);
            } else if (definition instanceof UnionTypeDefinition) {
                expandUnion((UnionTypeDefinition) definition, (SUUnionType) type, registry);
            } else if (definition instanceof EnumTypeDefinition) {
                expandEnum((EnumTypeDefinition) definition, (SUEnumType) type, registry);
            } else if (definition instanceof InputObjectTypeDefinition) {
                expandInputObject(
                        (InputObjectTypeDefinition) definition,
                        (SUInputObjectType) type,
                        registry);
            } else if (definition instanceof ScalarTypeDefinition) {
                expandScalar((ScalarTypeDefinition) definition, (SUScalarType) type, registry);
            }
        }
        for (ScalarTypeDefinition definition : registry.scalars().values()) {
            if (!registry.types().containsKey(definition.getName())) {
                expandScalar(
                        definition,
                        (SUScalarType) namedType(definition.getName()),
                        registry);
            }
        }
    }

    private void expandObject(
            ObjectTypeDefinition definition,
            SUObjectType type,
            ImmutableTypeDefinitionRegistry registry) {
        addFields(definition.getFieldDefinitions(), type);
        addImplementedInterfaces(definition.getImplements(), type);
        addAppliedDirectives(definition.getDirectives(), type);
        for (ObjectTypeExtensionDefinition extension :
                registry.objectTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addFields(extension.getFieldDefinitions(), type);
            addImplementedInterfaces(extension.getImplements(), type);
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void expandInterface(
            InterfaceTypeDefinition definition,
            SUInterfaceType type,
            ImmutableTypeDefinitionRegistry registry) {
        addFields(definition.getFieldDefinitions(), type);
        addImplementedInterfaces(definition.getImplements(), type);
        addAppliedDirectives(definition.getDirectives(), type);
        for (InterfaceTypeExtensionDefinition extension :
                registry.interfaceTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addFields(extension.getFieldDefinitions(), type);
            addImplementedInterfaces(extension.getImplements(), type);
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void addFields(List<FieldDefinition> definitions, SUVertex parent) {
        for (FieldDefinition definition : definitions) {
            SUField field = universe.newField(
                    definition.getName(),
                    description(definition, definition.getDescription()));
            addField(parent, field);
            builder().setFieldType(field, typeVertex(definition.getType()));
            for (InputValueDefinition argumentDefinition :
                    definition.getInputValueDefinitions()) {
                SUArgument argument = newArgument(argumentDefinition);
                builder().addArgument(field, argument);
                builder().setArgumentType(argument, typeVertex(argumentDefinition.getType()));
                addAppliedDirectives(argumentDefinition.getDirectives(), argument);
            }
            addAppliedDirectives(definition.getDirectives(), field);
        }
    }

    private void addField(SUVertex parent, SUField field) {
        if (parent instanceof SUObjectType) {
            builder().addField((SUObjectType) parent, field);
        } else {
            builder().addField((SUInterfaceType) parent, field);
        }
    }

    private SUArgument newArgument(InputValueDefinition definition) {
        return universe.newArgument(
                definition.getName(),
                description(definition, definition.getDescription()),
                inputValue(definition.getDefaultValue()),
                definition);
    }

    private void addImplementedInterfaces(List<Type> interfaces, SUVertex type) {
        for (Type<?> interfaceType : interfaces) {
            addInterface(type, (SUInterfaceType) namedType(typeName(interfaceType)));
        }
    }

    private void addInterface(SUVertex type, SUInterfaceType interfaceType) {
        if (type instanceof SUObjectType) {
            builder().addInterface((SUObjectType) type, interfaceType);
        } else {
            builder().addInterface((SUInterfaceType) type, interfaceType);
        }
    }

    private void expandUnion(
            UnionTypeDefinition definition,
            SUUnionType type,
            ImmutableTypeDefinitionRegistry registry) {
        addUnionMembers(definition.getMemberTypes(), type);
        addAppliedDirectives(definition.getDirectives(), type);
        for (UnionTypeExtensionDefinition extension :
                registry.unionTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addUnionMembers(extension.getMemberTypes(), type);
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void addUnionMembers(List<Type> memberTypes, SUUnionType type) {
        for (Type<?> memberType : memberTypes) {
            builder().addUnionMember(
                    type,
                    (SUObjectType) namedType(typeName(memberType)));
        }
    }

    private void expandEnum(
            EnumTypeDefinition definition,
            SUEnumType type,
            ImmutableTypeDefinitionRegistry registry) {
        addEnumValues(definition.getEnumValueDefinitions(), type);
        addAppliedDirectives(definition.getDirectives(), type);
        for (EnumTypeExtensionDefinition extension :
                registry.enumTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addEnumValues(extension.getEnumValueDefinitions(), type);
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void addEnumValues(List<EnumValueDefinition> definitions, SUEnumType type) {
        for (EnumValueDefinition definition : definitions) {
            SUEnumValue value = universe.newEnumValue(
                    definition.getName(),
                    description(definition, definition.getDescription()));
            builder().addEnumValue(type, value);
            addAppliedDirectives(definition.getDirectives(), value);
        }
    }

    private void expandInputObject(
            InputObjectTypeDefinition definition,
            SUInputObjectType type,
            ImmutableTypeDefinitionRegistry registry) {
        addInputFields(definition.getInputValueDefinitions(), type);
        addAppliedDirectives(definition.getDirectives(), type);
        for (InputObjectTypeExtensionDefinition extension :
                registry.inputObjectTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addInputFields(extension.getInputValueDefinitions(), type);
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void addInputFields(
            List<InputValueDefinition> definitions,
            SUInputObjectType type) {
        for (InputValueDefinition definition : definitions) {
            SUInputField field = universe.newInputField(
                    definition.getName(),
                    description(definition, definition.getDescription()),
                    inputValue(definition.getDefaultValue()),
                    definition);
            builder().addInputField(type, field);
            builder().setInputFieldType(field, typeVertex(definition.getType()));
            addAppliedDirectives(definition.getDirectives(), field);
        }
    }

    private void expandScalar(
            ScalarTypeDefinition definition,
            SUScalarType type,
            ImmutableTypeDefinitionRegistry registry) {
        addAppliedDirectives(definition.getDirectives(), type);
        for (ScalarTypeExtensionDefinition extension :
                registry.scalarTypeExtensions()
                        .getOrDefault(definition.getName(), Collections.emptyList())) {
            addAppliedDirectives(extension.getDirectives(), type);
        }
    }

    private void addOperationRoots(ImmutableTypeDefinitionRegistry registry) {
        Map<String, OperationTypeDefinition> operations = operationTypes(registry);
        OperationTypeDefinition query = operations.get("query");
        queryType = (SUObjectType) namedType(
                query == null ? "Query" : query.getTypeName().getName());
        builder().queryType(queryType);

        OperationTypeDefinition mutation = operations.get("mutation");
        if (mutation != null) {
            mutationType = (SUObjectType) namedType(mutation.getTypeName().getName());
            builder().mutationType(mutationType);
        } else if (registry.schemaDefinition().isEmpty()
                && namedTypes.get("Mutation") instanceof SUObjectType) {
            mutationType = (SUObjectType) namedType("Mutation");
            builder().mutationType(mutationType);
        }

        OperationTypeDefinition subscription = operations.get("subscription");
        if (subscription != null) {
            subscriptionType =
                    (SUObjectType) namedType(subscription.getTypeName().getName());
            builder().subscriptionType(subscriptionType);
        } else if (registry.schemaDefinition().isEmpty()
                && namedTypes.get("Subscription") instanceof SUObjectType) {
            subscriptionType = (SUObjectType) namedType("Subscription");
            builder().subscriptionType(subscriptionType);
        }
    }

    private Map<String, OperationTypeDefinition> operationTypes(
            ImmutableTypeDefinitionRegistry registry) {
        Map<String, OperationTypeDefinition> result = new LinkedHashMap<>();
        SchemaDefinition schemaDefinition = registry.schemaDefinition().orElse(null);
        if (schemaDefinition != null) {
            addOperationTypes(result, schemaDefinition.getOperationTypeDefinitions());
        }
        for (SchemaExtensionDefinition extension :
                registry.getSchemaExtensionDefinitions()) {
            addOperationTypes(result, extension.getOperationTypeDefinitions());
        }
        return result;
    }

    private void addOperationTypes(
            Map<String, OperationTypeDefinition> result,
            List<OperationTypeDefinition> definitions) {
        for (OperationTypeDefinition definition : definitions) {
            result.put(definition.getName(), definition);
        }
    }

    private void addSchemaDirectives(ImmutableTypeDefinitionRegistry registry) {
        SchemaDefinition schemaDefinition = registry.schemaDefinition().orElse(null);
        if (schemaDefinition != null) {
            addAppliedDirectives(schemaDefinition.getDirectives(), builder().getRoot());
        }
        for (SchemaExtensionDefinition extension :
                registry.getSchemaExtensionDefinitions()) {
            addAppliedDirectives(extension.getDirectives(), builder().getRoot());
        }
    }

    private void addTypes() {
        for (SUNamedType type : namedTypes.values()) {
            if (type == queryType || type == mutationType || type == subscriptionType) {
                continue;
            }
            if (type instanceof SUScalarType
                    && ScalarInfo.isGraphqlSpecifiedScalar(assertNotNull(type.getName()))
                    && !referencedTypeNames.contains(type.getName())) {
                continue;
            }
            builder().addType(type);
        }
    }

    private void addAppliedDirectives(
            List<Directive> directives,
            SUAppliedDirectiveContainer container) {
        for (Directive directive : directives) {
            SUDirective definition = assertNotNull(
                    directiveDefinitions.get(directive.getName()),
                    "Directive '%s' has no definition",
                    directive.getName());
            List<SUAppliedDirectiveArgument> arguments =
                    appliedArguments(directive, definition);
            SUAppliedDirective applied =
                    universe.newAppliedDirective(
                            directive.getName(),
                            arguments,
                            directive);
            builder().addAppliedDirective(container, applied);
        }
    }

    private List<SUAppliedDirectiveArgument> appliedArguments(
            Directive directive,
            SUDirective definition) {
        Map<String, SUArgument> definitions = assertNotNull(
                directiveArguments.get(definition.getName()));
        Map<String, Argument> explicit = new LinkedHashMap<>();
        List<SUAppliedDirectiveArgument> result = new ArrayList<>();
        for (Argument argument : directive.getArguments()) {
            explicit.put(argument.getName(), argument);
            result.add(appliedArgument(
                    definitions.get(argument.getName()),
                    inputValue(argument.getValue()),
                    argument));
        }
        for (SUArgument argument : definitions.values()) {
            if (explicit.containsKey(argument.getName())) {
                continue;
            }
            result.add(appliedArgument(
                    argument,
                    argument.getArgumentDefaultValue(),
                    null));
        }
        return result;
    }

    private SUAppliedDirectiveArgument appliedArgument(
            @Nullable SUArgument definition,
            InputValueWithState value,
            @Nullable Argument argumentDefinition) {
        SUArgument requiredDefinition = assertNotNull(definition);
        return universe.newAppliedDirectiveArgument(
                assertNotNull(requiredDefinition.getName()),
                assertNotNull(directiveArgumentTypes.get(requiredDefinition)),
                value,
                argumentDefinition);
    }

    private SUType typeVertex(Type<?> type) {
        if (type instanceof TypeName) {
            String name = ((TypeName) type).getName();
            referencedTypeNames.add(name);
            return namedType(name);
        }
        if (type instanceof ListType) {
            SUListType list = universe.newListType();
            builder().setWrappedType(list, typeVertex(((ListType) type).getType()));
            return list;
        }
        if (type instanceof NonNullType) {
            SUNonNullType nonNull = universe.newNonNullType();
            builder().setWrappedType(nonNull, typeVertex(((NonNullType) type).getType()));
            return nonNull;
        }
        return assertShouldNeverHappen("Unsupported type AST %s", type.getClass().getName());
    }

    private String typeName(Type<?> type) {
        Type<?> current = type;
        while (!(current instanceof TypeName)) {
            if (current instanceof ListType) {
                current = ((ListType) current).getType();
            } else if (current instanceof NonNullType) {
                current = ((NonNullType) current).getType();
            } else {
                return assertShouldNeverHappen(
                        "Unsupported type AST %s",
                        current.getClass().getName());
            }
        }
        return ((TypeName) current).getName();
    }

    private SUNamedType namedType(String name) {
        return assertNotNull(namedTypes.get(name), "Unknown type '%s'", name);
    }

    private InputValueWithState inputValue(@Nullable Value<?> value) {
        return value == null
                ? InputValueWithState.NOT_SET
                : InputValueWithState.newLiteralValue(value);
    }

    private @Nullable String schemaDescription(
            ImmutableTypeDefinitionRegistry registry) {
        SchemaDefinition definition = registry.schemaDefinition().orElse(null);
        return definition == null
                ? null
                : description(definition, definition.getDescription());
    }

    private @Nullable String description(
            Node<?> node,
            @Nullable Description description) {
        if (description != null) {
            return description.getContent();
        }
        List<String> lines = new ArrayList<>();
        for (Comment comment : node.getComments()) {
            String line = comment.getContent();
            if (line.trim().isEmpty()) {
                lines.clear();
            } else {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private SUSchemaBuilder builder() {
        return assertNotNull(schemaBuilder, "Schema generation has not started");
    }
}
