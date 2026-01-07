package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.NamedNode;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.DirectiveIllegalLocationError;
import graphql.schema.idl.errors.DirectiveIllegalReferenceError;
import graphql.schema.idl.errors.DirectiveMissingNonNullArgumentError;
import graphql.schema.idl.errors.DirectiveUndeclaredError;
import graphql.schema.idl.errors.DirectiveUnknownArgumentError;
import graphql.schema.idl.errors.IllegalNameError;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.NotAnInputTypeError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.mergeFirst;

/**
 * This is responsible for traversing EVERY type and field in the registry and ensuring that
 * any directives used follow the directive definition rules, for example
 * field directives can be used on object types
 */
@Internal
class SchemaTypeDirectivesChecker {

    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;

    public SchemaTypeDirectivesChecker(final TypeDefinitionRegistry typeRegistry,
                                       final RuntimeWiring runtimeWiring) {
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
    }

    void checkTypeDirectives(List<GraphQLError> errors) {
        typeRegistry.objectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(OBJECT, errors, ext)));
        typeRegistry.interfaceTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INTERFACE, errors, ext)));
        typeRegistry.unionTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(UNION, errors, ext)));
        typeRegistry.enumTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(ENUM, errors, ext)));
        typeRegistry.scalarTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(SCALAR, errors, ext)));
        typeRegistry.inputObjectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INPUT_OBJECT, errors, ext)));

        typeRegistry.getTypes(ObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(OBJECT, errors, typeDef));
        typeRegistry.getTypes(InterfaceTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INTERFACE, errors, typeDef));
        typeRegistry.getTypes(UnionTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(UNION, errors, typeDef));
        typeRegistry.getTypes(EnumTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(ENUM, errors, typeDef));
        typeRegistry.getTypes(InputObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INPUT_OBJECT, errors, typeDef));

        typeRegistry.scalars().values()
                .forEach(typeDef -> checkDirectives(SCALAR, errors, typeDef));

        List<Directive> schemaDirectives = SchemaExtensionsChecker.gatherSchemaDirectives(typeRegistry, errors);
        // we need to have a Node for error reporting so we make one in case there is not one
        SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().orElse(SchemaDefinition.newSchemaDefinition().build());
        checkDirectives(DirectiveLocation.SCHEMA, errors, typeRegistry, schemaDefinition, "schema", schemaDirectives);

        Collection<DirectiveDefinition> directiveDefinitions = typeRegistry.getDirectiveDefinitions().values();
        commonCheck(directiveDefinitions, errors);
    }


    private void checkDirectives(DirectiveLocation expectedLocation, List<GraphQLError> errors, TypeDefinition<?> typeDef) {
        checkDirectives(expectedLocation, errors, typeRegistry, typeDef, typeDef.getName(), typeDef.getDirectives());

        if (typeDef instanceof ObjectTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((ObjectTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof InterfaceTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((InterfaceTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof EnumTypeDefinition) {
            List<EnumValueDefinition> enumValueDefinitions = ((EnumTypeDefinition) typeDef).getEnumValueDefinitions();
            enumValueDefinitions.forEach(definition -> checkDirectives(ENUM_VALUE, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
        if (typeDef instanceof InputObjectTypeDefinition) {
            List<InputValueDefinition> inputValueDefinitions = ((InputObjectTypeDefinition) typeDef).getInputValueDefinitions();
            inputValueDefinitions.forEach(definition -> checkDirectives(INPUT_FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
    }

    private void checkFieldsDirectives(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, List<FieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(definition -> {
            checkDirectives(FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives());
            //
            // and check its arguments
            definition.getInputValueDefinitions().forEach(arg -> checkDirectives(ARGUMENT_DEFINITION, errors, typeRegistry, arg, arg.getName(), arg.getDirectives()));
        });
    }

    private void checkDirectives(DirectiveLocation expectedLocation, List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Node<?> element, String elementName, List<Directive> directives) {
        directives.forEach(directive -> {
            Optional<DirectiveDefinition> directiveDefinition = typeRegistry.getDirectiveDefinition(directive.getName());
            if (directiveDefinition.isEmpty()) {
                errors.add(new DirectiveUndeclaredError(element, elementName, directive.getName()));
            } else {
                if (!inRightLocation(expectedLocation, directiveDefinition.get())) {
                    errors.add(new DirectiveIllegalLocationError(element, elementName, directive.getName(), expectedLocation.name()));
                }
                checkDirectiveArguments(errors, typeRegistry, element, elementName, directive, directiveDefinition.get());
            }
        });
    }

    private static boolean inRightLocation(DirectiveLocation expectedLocation, DirectiveDefinition directiveDefinition) {
        for (graphql.language.DirectiveLocation location : directiveDefinition.getDirectiveLocations()) {
            if (location.getName().equalsIgnoreCase(expectedLocation.name())) {
                return true;
            }
        }
        return false;
    }

    private void checkDirectiveArguments(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Node<?> element, String elementName, Directive directive, DirectiveDefinition directiveDefinition) {
        Map<String, InputValueDefinition> allowedArgs = getByName(directiveDefinition.getInputValueDefinitions(), (InputValueDefinition::getName), mergeFirst());
        Map<String, Argument> providedArgs = getByName(directive.getArguments(), (Argument::getName), mergeFirst());
        directive.getArguments().forEach(argument -> {
            InputValueDefinition allowedArg = allowedArgs.get(argument.getName());
            if (allowedArg == null) {
                errors.add(new DirectiveUnknownArgumentError(element, elementName, directive.getName(), argument.getName()));
            } else {
                ArgValueOfAllowedTypeChecker argValueOfAllowedTypeChecker = new ArgValueOfAllowedTypeChecker(directive, element, elementName, argument, typeRegistry, runtimeWiring);
                argValueOfAllowedTypeChecker.checkArgValueMatchesAllowedType(errors, argument.getValue(), allowedArg.getType());
            }
        });
        allowedArgs.forEach((argName, definitionArgument) -> {
            if (isNoNullArgWithoutDefaultValue(definitionArgument)) {
                if (!providedArgs.containsKey(argName)) {
                    errors.add(new DirectiveMissingNonNullArgumentError(element, elementName, directive.getName(), argName));
                }
            }
        });
    }

    private static boolean isNoNullArgWithoutDefaultValue(InputValueDefinition definitionArgument) {
        return definitionArgument.getType() instanceof NonNullType && definitionArgument.getDefaultValue() == null;
    }

    private void commonCheck(Collection<DirectiveDefinition> directiveDefinitions, List<GraphQLError> errors) {
        List<DirectiveDefinition> directiveDefinitionList = new ArrayList<>(directiveDefinitions);
        Map<String, DirectiveDefinition> directiveDefinitionMap = getByName(directiveDefinitionList, DirectiveDefinition::getName, mergeFirst());
        Set<String> reportedCycles = new HashSet<>();

        directiveDefinitions.forEach(directiveDefinition -> {
            assertTypeName(directiveDefinition, errors);
            directiveDefinition.getInputValueDefinitions().forEach(inputValueDefinition -> {
                assertTypeName(inputValueDefinition, errors);
                assertExistAndIsInputType(inputValueDefinition, errors);
                if (inputValueDefinition.hasDirective(directiveDefinition.getName())) {
                    errors.add(new DirectiveIllegalReferenceError(directiveDefinition, inputValueDefinition));
                }
            });

            // Check for indirect cycles (A -> B -> A, or A -> B -> C -> A)
            List<String> cyclePath = new ArrayList<>();
            cyclePath.add(directiveDefinition.getName());
            if (hasDirectiveCycle(directiveDefinition, directiveDefinitionMap, cyclePath, new LinkedHashSet<>())) {
                // Only report each cycle once (use a canonical representation to avoid duplicates)
                String cycleKey = getCycleKey(cyclePath);
                if (!reportedCycles.contains(cycleKey)) {
                    reportedCycles.add(cycleKey);
                    errors.add(new DirectiveIllegalReferenceError(directiveDefinition, cyclePath));
                }
            }
        });
    }

    /**
     * Detects if a directive has a cycle through applied directives on its arguments.
     *
     * @param directiveDefinition the directive to check
     * @param directiveDefinitionMap map of all directive definitions by name
     * @param path the current path being explored (for error reporting)
     * @param visited set of directive names currently in the recursion stack
     * @return true if a cycle is detected
     */
    private boolean hasDirectiveCycle(DirectiveDefinition directiveDefinition,
                                      Map<String, DirectiveDefinition> directiveDefinitionMap,
                                      List<String> path,
                                      Set<String> visited) {
        String directiveName = directiveDefinition.getName();

        // If already in the current path, we have found a cycle
        if (visited.contains(directiveName)) {
            return true;
        }

        visited.add(directiveName);

        // Check all input value definitions (arguments) of this directive
        for (InputValueDefinition inputValueDefinition : directiveDefinition.getInputValueDefinitions()) {
            // Get all directives applied to this argument
            for (Directive appliedDirective : inputValueDefinition.getDirectives()) {
                String appliedDirectiveName = appliedDirective.getName();

                // Skip self-reference (already handled separately with more specific error)
                if (appliedDirectiveName.equals(directiveName)) {
                    continue;
                }

                DirectiveDefinition referencedDirective = directiveDefinitionMap.get(appliedDirectiveName);
                if (referencedDirective != null) {
                    path.add(appliedDirectiveName);
                    if (hasDirectiveCycle(referencedDirective, directiveDefinitionMap, path, visited)) {
                        return true;
                    }
                    path.remove(path.size() - 1);
                }
            }
        }

        visited.remove(directiveName);
        return false;
    }

    /**
     * Creates a canonical key for a cycle to avoid reporting the same cycle multiple times.
     * The key is the smallest rotation of the cycle path (excluding the last element which is the same as the first).
     */
    private static String getCycleKey(List<String> cyclePath) {
        if (cyclePath.size() <= 1) {
            return String.join("->", cyclePath);
        }

        // Remove the last element (same as first) for comparison
        List<String> cycleWithoutLast = cyclePath.subList(0, cyclePath.size() - 1);

        // Find the lexicographically smallest rotation
        String smallest = String.join("->", cycleWithoutLast);
        for (int i = 1; i < cycleWithoutLast.size(); i++) {
            List<String> rotated = new ArrayList<>();
            for (int j = 0; j < cycleWithoutLast.size(); j++) {
                rotated.add(cycleWithoutLast.get((i + j) % cycleWithoutLast.size()));
            }
            String rotatedStr = String.join("->", rotated);
            if (rotatedStr.compareTo(smallest) < 0) {
                smallest = rotatedStr;
            }
        }
        return smallest;
    }

    private static void assertTypeName(NamedNode<?> node, List<GraphQLError> errors) {
        if (node.getName().length() >= 2 && node.getName().startsWith("__")) {
            errors.add((new IllegalNameError(node)));
        }
    }

    public void assertExistAndIsInputType(InputValueDefinition definition, List<GraphQLError> errors) {
        TypeName namedType = TypeUtil.unwrapAll(definition.getType());

        TypeDefinition<?> unwrappedType = findTypeDefFromRegistry(namedType.getName(), typeRegistry);

        if (unwrappedType == null) {
            errors.add(new MissingTypeError(namedType.getName(), definition, definition.getName()));
            return;
        }

        if (!(unwrappedType instanceof InputObjectTypeDefinition)
                && !(unwrappedType instanceof EnumTypeDefinition)
                && !(unwrappedType instanceof ScalarTypeDefinition)) {
            errors.add(new NotAnInputTypeError(namedType, unwrappedType));
        }
    }

    private static TypeDefinition<?> findTypeDefFromRegistry(String typeName, TypeDefinitionRegistry typeRegistry) {
        TypeDefinition<?> typeDefinition = typeRegistry.getTypeOrNull(typeName);
        if (typeDefinition != null) {
            return typeDefinition;
        }
        return typeRegistry.scalars().get(typeName);
    }
}