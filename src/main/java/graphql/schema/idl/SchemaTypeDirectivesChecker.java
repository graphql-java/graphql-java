package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
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
import graphql.language.NamedNode;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
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
import java.util.Collections;
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
        directiveDefinitions.forEach(directiveDefinition -> {
            assertTypeName(directiveDefinition, errors);
            boolean hasDirectSelfReference = false;
            for (InputValueDefinition inputValueDefinition : directiveDefinition.getInputValueDefinitions()) {
                assertTypeName(inputValueDefinition, errors);
                assertExistAndIsInputType(inputValueDefinition, errors);
                if (inputValueDefinition.hasDirective(directiveDefinition.getName())) {
                    errors.add(new DirectiveIllegalReferenceError(directiveDefinition, inputValueDefinition));
                    hasDirectSelfReference = true;
                }
            }
            if (hasDirectSelfReference) {
                return;
            }

            String cycle = findDirectiveCycle(directiveDefinition);
            if (cycle != null) {
                errors.add(new DirectiveIllegalReferenceError(directiveDefinition, cycle));
            }
        });
    }

    private String findDirectiveCycle(DirectiveDefinition directiveDefinition) {
        List<String> path = new ArrayList<>();
        path.add(directiveName(directiveDefinition.getName()));

        Set<String> visited = new LinkedHashSet<>();
        visited.add(directiveName(directiveDefinition.getName()));

        return findCycleFromInputValueDefinitions(directiveDefinition.getInputValueDefinitions(), directiveDefinition.getName(), path, visited);
    }

    private String findCycleFromInputValueDefinitions(List<InputValueDefinition> inputValueDefinitions, String startDirectiveName, List<String> path, Set<String> visited) {
        for (InputValueDefinition inputValueDefinition : inputValueDefinitions) {
            String cycle = findCycleFromDirectives(inputValueDefinition.getDirectives(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }

            cycle = findCycleFromInputType(inputValueDefinition.getType(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromDirectives(List<Directive> directives, String startDirectiveName, List<String> path, Set<String> visited) {
        for (Directive directive : directives) {
            String cycle = findCycleFromDirectiveReference(directive.getName(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromDirectiveReference(String directiveName, String startDirectiveName, List<String> path, Set<String> visited) {
        String displayName = directiveName(directiveName);
        if (directiveName.equals(startDirectiveName)) {
            return cyclePath(path, displayName);
        }
        if (visited.contains(displayName)) {
            return null;
        }

        Optional<DirectiveDefinition> directiveDefinition = typeRegistry.getDirectiveDefinition(directiveName);
        if (directiveDefinition.isEmpty()) {
            return null;
        }

        return findCycleFromInputValueDefinitions(
                directiveDefinition.get().getInputValueDefinitions(),
                startDirectiveName,
                addToPath(path, displayName),
                addToVisited(visited, displayName)
        );
    }

    private String findCycleFromInputType(graphql.language.Type<?> type, String startDirectiveName, List<String> path, Set<String> visited) {
        TypeDefinition<?> typeDefinition = findTypeDefFromRegistry(TypeUtil.unwrapAll(type).getName(), typeRegistry);
        if (typeDefinition == null) {
            return null;
        }
        if (visited.contains(typeDefinition.getName())) {
            return null;
        }

        List<String> nextPath = addToPath(path, typeDefinition.getName());
        Set<String> nextVisited = addToVisited(visited, typeDefinition.getName());

        if (typeDefinition instanceof ScalarTypeDefinition) {
            return findCycleFromScalarType((ScalarTypeDefinition) typeDefinition, startDirectiveName, nextPath, nextVisited);
        }
        if (typeDefinition instanceof EnumTypeDefinition) {
            return findCycleFromEnumType((EnumTypeDefinition) typeDefinition, startDirectiveName, nextPath, nextVisited);
        }
        if (typeDefinition instanceof InputObjectTypeDefinition) {
            return findCycleFromInputObjectType((InputObjectTypeDefinition) typeDefinition, startDirectiveName, nextPath, nextVisited);
        }
        return null;
    }

    private String findCycleFromScalarType(ScalarTypeDefinition typeDefinition, String startDirectiveName, List<String> path, Set<String> visited) {
        String cycle = findCycleFromDirectives(typeDefinition.getDirectives(), startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        List<ScalarTypeExtensionDefinition> extensions = typeRegistry.scalarTypeExtensions().getOrDefault(typeDefinition.getName(), Collections.emptyList());
        for (ScalarTypeExtensionDefinition extension : extensions) {
            cycle = findCycleFromDirectives(extension.getDirectives(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromEnumType(EnumTypeDefinition typeDefinition, String startDirectiveName, List<String> path, Set<String> visited) {
        String cycle = findCycleFromDirectives(typeDefinition.getDirectives(), startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        cycle = findCycleFromEnumValues(typeDefinition.getEnumValueDefinitions(), startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        List<EnumTypeExtensionDefinition> extensions = typeRegistry.enumTypeExtensions().getOrDefault(typeDefinition.getName(), Collections.emptyList());
        for (EnumTypeExtensionDefinition extension : extensions) {
            cycle = findCycleFromDirectives(extension.getDirectives(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }

            cycle = findCycleFromEnumValues(extension.getEnumValueDefinitions(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromEnumValues(List<EnumValueDefinition> enumValueDefinitions, String startDirectiveName, List<String> path, Set<String> visited) {
        for (EnumValueDefinition enumValueDefinition : enumValueDefinitions) {
            String cycle = findCycleFromDirectives(enumValueDefinition.getDirectives(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromInputObjectType(InputObjectTypeDefinition typeDefinition, String startDirectiveName, List<String> path, Set<String> visited) {
        String cycle = findCycleFromDirectives(typeDefinition.getDirectives(), startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        cycle = findCycleFromInputValueDefinitions(typeDefinition.getInputValueDefinitions(), startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        List<InputObjectTypeExtensionDefinition> extensions = typeRegistry.inputObjectTypeExtensions().getOrDefault(typeDefinition.getName(), Collections.emptyList());
        for (InputObjectTypeExtensionDefinition extension : extensions) {
            cycle = findCycleFromDirectives(extension.getDirectives(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }

            cycle = findCycleFromInputValueDefinitions(extension.getInputValueDefinitions(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private List<String> addToPath(List<String> path, String element) {
        List<String> nextPath = new ArrayList<>(path);
        nextPath.add(element);
        return nextPath;
    }

    private Set<String> addToVisited(Set<String> visited, String element) {
        Set<String> nextVisited = new LinkedHashSet<>(visited);
        nextVisited.add(element);
        return nextVisited;
    }

    private String cyclePath(List<String> path, String cycleElement) {
        return String.join(" -> ", addToPath(path, cycleElement));
    }

    private String directiveName(String directiveName) {
        return "@" + directiveName;
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
