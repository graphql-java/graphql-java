package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.DirectiveIllegalLocationError;
import graphql.schema.idl.errors.MissingInterfaceTypeError;
import graphql.schema.idl.errors.MissingScalarImplementationError;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.MissingTypeResolverError;
import graphql.schema.idl.errors.NonUniqueArgumentError;
import graphql.schema.idl.errors.NonUniqueNameError;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static java.util.stream.Collectors.toList;

/**
 * This helps pre check the state of the type system to ensure it can be made into an executable schema.
 * <p>
 * It looks for missing types and ensure certain invariants are true before a schema can be made.
 */
@Internal
public class SchemaTypeChecker {

    public List<GraphQLError> checkTypeRegistry(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {
        List<GraphQLError> errors = new ArrayList<>();
        checkForMissingTypes(errors, typeRegistry);

        SchemaTypeExtensionsChecker typeExtensionsChecker = new SchemaTypeExtensionsChecker();

        typeExtensionsChecker.checkTypeExtensions(errors, typeRegistry);

        ImplementingTypesChecker implementingTypesChecker = new ImplementingTypesChecker();
        implementingTypesChecker.checkImplementingTypes(errors, typeRegistry);

        UnionTypesChecker unionTypesChecker = new UnionTypesChecker();
        unionTypesChecker.checkUnionType(errors, typeRegistry);

        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry);

        checkScalarImplementationsArePresent(errors, typeRegistry, wiring);
        checkTypeResolversArePresent(errors, typeRegistry, wiring);

        checkFieldsAreSensible(errors, typeRegistry);

        //check directive definitions before checking directive usages
        checkDirectiveDefinitions(typeRegistry, errors);

        SchemaTypeDirectivesChecker directivesChecker = new SchemaTypeDirectivesChecker(typeRegistry, wiring);
        directivesChecker.checkTypeDirectives(errors);

        return errors;
    }

    private void checkForMissingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        // type extensions
        List<ObjectTypeExtensionDefinition> typeExtensions = typeRegistry.objectTypeExtensions().values().stream().flatMap(Collection::stream).collect(toList());
        typeExtensions.forEach(typeExtension -> {

            List<Type> implementsTypes = typeExtension.getImplements();
            implementsTypes.forEach(checkInterfaceTypeExists(typeRegistry, errors, typeExtension));

            checkFieldTypesPresent(typeRegistry, errors, typeExtension, typeExtension.getFieldDefinitions());

        });


        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> {

            List<Type> implementsTypes = objectType.getImplements();
            implementsTypes.forEach(checkInterfaceTypeExists(typeRegistry, errors, objectType));

            checkFieldTypesPresent(typeRegistry, errors, objectType, objectType.getFieldDefinitions());

        });

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> {
            List<FieldDefinition> fields = interfaceType.getFieldDefinitions();

            checkFieldTypesPresent(typeRegistry, errors, interfaceType, fields);

        });

        // union types
        List<UnionTypeDefinition> unionTypes = filterTo(typesMap, UnionTypeDefinition.class);
        unionTypes.forEach(unionType -> {
            List<Type> memberTypes = unionType.getMemberTypes();
            memberTypes.forEach(checkTypeExists("union member", typeRegistry, errors, unionType));

        });


        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> {
            List<InputValueDefinition> inputValueDefinitions = inputType.getInputValueDefinitions();
            List<Type> inputValueTypes = inputValueDefinitions.stream()
                    .map(InputValueDefinition::getType)
                    .collect(toList());

            inputValueTypes.forEach(checkTypeExists("input value", typeRegistry, errors, inputType));

        });
    }

    private void checkDirectiveDefinitions(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {

        List<DirectiveDefinition> directiveDefinitions = new ArrayList<>(typeRegistry.getDirectiveDefinitions().values());

        directiveDefinitions.forEach(directiveDefinition -> {
            List<InputValueDefinition> arguments = directiveDefinition.getInputValueDefinitions();

            checkNamedUniqueness(errors, arguments, InputValueDefinition::getName,
                    (name, arg) -> new NonUniqueNameError(directiveDefinition, arg));

            List<Type> inputValueTypes = arguments.stream()
                    .map(InputValueDefinition::getType)
                    .collect(toList());

            inputValueTypes.forEach(
                    checkTypeExists(typeRegistry, errors, "directive definition", directiveDefinition, directiveDefinition.getName())
            );

            directiveDefinition.getDirectiveLocations().forEach(directiveLocation -> {
                String locationName = directiveLocation.getName();
                try {
                    Introspection.DirectiveLocation.valueOf(locationName);
                } catch (IllegalArgumentException e) {
                    errors.add(new DirectiveIllegalLocationError(directiveDefinition, locationName));
                }
            });
        });
    }

    private void checkScalarImplementationsArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
        typeRegistry.scalars().forEach((scalarName, scalarTypeDefinition) -> {
            WiringFactory wiringFactory = wiring.getWiringFactory();
            ScalarWiringEnvironment environment = new ScalarWiringEnvironment(typeRegistry, scalarTypeDefinition, Collections.emptyList());
            if (!wiringFactory.providesScalar(environment) && !wiring.getScalars().containsKey(scalarName)) {
                errors.add(new MissingScalarImplementationError(scalarName));
            }
        });
    }

    private void checkFieldsAreSensible(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        Map<String, DirectiveDefinition> directiveDefinitionMap = typeRegistry.getDirectiveDefinitions();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> checkObjTypeFields(errors, objectType, objectType.getFieldDefinitions(), directiveDefinitionMap));

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> checkInterfaceFields(errors, interfaceType, interfaceType.getFieldDefinitions(), directiveDefinitionMap));

        // enum types
        List<EnumTypeDefinition> enumTypes = filterTo(typesMap, EnumTypeDefinition.class);
        enumTypes.forEach(enumType -> checkEnumValues(errors, enumType, enumType.getEnumValueDefinitions(), directiveDefinitionMap));

        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> checkInputValues(errors, inputType, inputType.getInputValueDefinitions(), INPUT_FIELD_DEFINITION, directiveDefinitionMap));
    }

    private void checkObjTypeFields(List<GraphQLError> errors, ObjectTypeDefinition typeDefinition, List<FieldDefinition> fieldDefinitions, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        // field unique ness
        checkNamedUniqueness(errors, fieldDefinitions, FieldDefinition::getName,
                (name, fieldDef) -> new NonUniqueNameError(typeDefinition, fieldDef));

        // field arg unique ness
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueArgumentError(typeDefinition, fld, name)));

        // directive checks
        fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive -> {

            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(typeDefinition, fld, argumentName));

        }));
    }

    private void checkInterfaceFields(List<GraphQLError> errors, InterfaceTypeDefinition interfaceType, List<FieldDefinition> fieldDefinitions, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        // field unique ness
        checkNamedUniqueness(errors, fieldDefinitions, FieldDefinition::getName,
                (name, fieldDef) -> new NonUniqueNameError(interfaceType, fieldDef));

        // field arg unique ness
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueArgumentError(interfaceType, fld, name)));

        // directive checks
        fieldDefinitions.forEach(fieldDefinition -> {
            List<Directive> directives = fieldDefinition.getDirectives();

            directives.forEach(directive -> checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(interfaceType, fieldDefinition, argumentName)));
        });
    }

    private void checkEnumValues(List<GraphQLError> errors, EnumTypeDefinition enumType, List<EnumValueDefinition> enumValueDefinitions, Map<String, DirectiveDefinition> directiveDefinitionMap) {

        // enum unique ness
        checkNamedUniqueness(errors, enumValueDefinitions, EnumValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueNameError(enumType, inputValueDefinition));


        // directive checks
        enumValueDefinitions.forEach(enumValue -> enumValue.getDirectives().forEach(directive -> {

            BiFunction<String, Argument, NonUniqueArgumentError> errorFunction = (argumentName, argument) -> new NonUniqueArgumentError(enumType, enumValue, argumentName);
            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName, errorFunction);

        }));
    }

    private void checkInputValues(List<GraphQLError> errors, InputObjectTypeDefinition inputType, List<InputValueDefinition> inputValueDefinitions, Introspection.DirectiveLocation directiveLocation, Map<String, DirectiveDefinition> directiveDefinitionMap) {

        // field unique ness
        checkNamedUniqueness(errors, inputValueDefinitions, InputValueDefinition::getName,
                (name, inputValueDefinition) -> {
                    // not sure why this is needed but inlining breaks it
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    InputObjectTypeDefinition as = inputType;
                    return new NonUniqueNameError(as, inputValueDefinition);
                });


        // directive checks
        inputValueDefinitions.forEach(inputValueDef -> inputValueDef.getDirectives().forEach(directive ->
                checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                        (argumentName, argument) -> new NonUniqueArgumentError(inputType, inputValueDef, argumentName))));
    }

    /**
     * A simple function that takes a list of things, asks for their names and checks that the
     * names are unique within that list.  If not it calls the error handler function
     *
     * @param errors            the error list
     * @param listOfNamedThings the list of named things
     * @param namer             the function naming a thing
     * @param errorFunction     the function producing an error
     */
    static <T, E extends GraphQLError> void checkNamedUniqueness(List<GraphQLError> errors, List<T> listOfNamedThings, Function<T, String> namer, BiFunction<String, T, E> errorFunction) {
        Set<String> names = new LinkedHashSet<>();
        listOfNamedThings.forEach(thing -> {
            String name = namer.apply(thing);
            if (names.contains(name)) {
                errors.add(errorFunction.apply(name, thing));
            } else {
                names.add(name);
            }
        });
    }

    private void checkTypeResolversArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {

        Predicate<InterfaceTypeDefinition> noDynamicResolverForInterface = interfaceTypeDef -> !wiring.getWiringFactory().providesTypeResolver(new InterfaceWiringEnvironment(typeRegistry, interfaceTypeDef));
        Predicate<UnionTypeDefinition> noDynamicResolverForUnion = unionTypeDef -> !wiring.getWiringFactory().providesTypeResolver(new UnionWiringEnvironment(typeRegistry, unionTypeDef));

        Predicate<TypeDefinition> noTypeResolver = typeDefinition -> !wiring.getTypeResolvers().containsKey(typeDefinition.getName());
        Consumer<TypeDefinition> addError = typeDefinition -> errors.add(new MissingTypeResolverError(typeDefinition));

        typeRegistry.types().values().stream()
                .filter(typeDef -> typeDef instanceof InterfaceTypeDefinition)
                .map(InterfaceTypeDefinition.class::cast)
                .filter(noDynamicResolverForInterface)
                .filter(noTypeResolver)
                .forEach(addError);

        typeRegistry.types().values().stream()
                .filter(typeDef -> typeDef instanceof UnionTypeDefinition)
                .map(UnionTypeDefinition.class::cast)
                .filter(noDynamicResolverForUnion)
                .filter(noTypeResolver)
                .forEach(addError);

    }

    private void checkFieldTypesPresent(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fields) {
        List<Type> fieldTypes = fields.stream().map(FieldDefinition::getType).collect(toList());
        fieldTypes.forEach(checkTypeExists("field", typeRegistry, errors, typeDefinition));

        List<Type> fieldInputValues = fields.stream()
                .map(f -> f.getInputValueDefinitions()
                        .stream()
                        .map(InputValueDefinition::getType)
                        .collect(toList()))
                .flatMap(Collection::stream)
                .collect(toList());

        fieldInputValues.forEach(checkTypeExists("field input", typeRegistry, errors, typeDefinition));
    }


    private Consumer<Type> checkTypeExists(String typeOfType, TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeName unwrapped = TypeInfo.typeInfo(t).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError(typeOfType, typeDefinition, unwrapped));
            }
        };
    }

    private Consumer<Type> checkTypeExists(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, String typeOfType, Node element, String elementName) {
        return ivType -> {
            TypeName unwrapped = TypeInfo.typeInfo(ivType).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError(typeOfType, element, elementName, unwrapped));
            }
        };
    }

    private Consumer<? super Type> checkInterfaceTypeExists(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeInfo typeInfo = TypeInfo.typeInfo(t);
            TypeName unwrapped = typeInfo.getTypeName();
            Optional<TypeDefinition> type = typeRegistry.getType(unwrapped);
            if (!type.isPresent()) {
                errors.add(new MissingInterfaceTypeError("interface", typeDefinition, unwrapped));
            } else if (!(type.get() instanceof InterfaceTypeDefinition)) {
                errors.add(new MissingInterfaceTypeError("interface", typeDefinition, unwrapped));
            }
        };
    }

    private <T extends TypeDefinition> List<T> filterTo(Map<String, TypeDefinition> types, Class<? extends T> clazz) {
        return types.values().stream()
                .filter(t -> clazz.equals(t.getClass()))
                .map(clazz::cast)
                .collect(toList());
    }
}
