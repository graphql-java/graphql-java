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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.DIRECTIVE_DEFINITION;
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
import static java.util.stream.Collectors.joining;

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
        directiveDefinitions.forEach(definition -> {
            checkDirectives(DIRECTIVE_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives());
            definition.getInputValueDefinitions().forEach(argument ->
                    checkDirectives(ARGUMENT_DEFINITION, errors, typeRegistry, argument, argument.getName(), argument.getDirectives()));
        });
        typeRegistry.directiveExtensions().values().forEach(extensions ->
                extensions.forEach(extension ->
                        checkDirectives(DIRECTIVE_DEFINITION, errors, typeRegistry, extension, extension.getName(), extension.getDirectives())));
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
        List<DirectiveDefinition> directiveDefinitionsList = new ArrayList<>(directiveDefinitions);
        Map<String, DirectiveDefinition> directiveDefinitionsByName = getByName(directiveDefinitionsList, DirectiveDefinition::getName, mergeFirst());
        Map<String, Map<String, NamedNode<?>>> referencesByName = referencesByName(directiveDefinitionsByName);

        directiveDefinitions.forEach(directiveDefinition -> {
            assertTypeName(directiveDefinition, errors);
            checkDirectReference(directiveDefinition, directiveDefinition, directiveDefinition.getDirectives(), errors);
            directiveDefinition.getInputValueDefinitions().forEach(inputValueDefinition -> {
                assertTypeName(inputValueDefinition, errors);
                assertExistAndIsInputType(inputValueDefinition, errors);
                checkDirectReference(directiveDefinition, inputValueDefinition, inputValueDefinition.getDirectives(), errors);
            });
            typeRegistry.directiveExtensions()
                    .getOrDefault(directiveDefinition.getName(), Collections.emptyList())
                    .forEach(extension ->
                            checkDirectReference(directiveDefinition, extension, extension.getDirectives(), errors));
        });
        checkIndirectDirectiveCycles(directiveDefinitionsByName, referencesByName, errors);
    }

    private static void checkDirectReference(DirectiveDefinition definition,
                                             NamedNode<?> location,
                                             List<Directive> directives,
                                             List<GraphQLError> errors) {
        if (directives.stream().noneMatch(directive -> directive.getName().equals(definition.getName()))) {
            return;
        }
        errors.add(new DirectiveIllegalReferenceError(definition, location));
    }

    private Map<String, Map<String, NamedNode<?>>> referencesByName(
            Map<String, DirectiveDefinition> directiveDefinitionsByName) {
        Map<String, Map<String, NamedNode<?>>> result = new LinkedHashMap<>();
        directiveDefinitionsByName.values().forEach(definition ->
                recordDirectiveDefinitionReferences(result, definition));
        recordInputTypeReferences(result);
        return result;
    }

    private void recordDirectiveDefinitionReferences(Map<String, Map<String, NamedNode<?>>> references,
                                                     DirectiveDefinition definition) {
        String source = definition.getName();
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getInputValueDefinitions().forEach(argument -> {
            recordAppliedDirectiveReferences(references, source, argument, argument.getDirectives());
            recordReference(references, source, typeKey(TypeUtil.unwrapAll(argument.getType()).getName()), argument);
        });
        typeRegistry.directiveExtensions()
                .getOrDefault(source, Collections.emptyList())
                .forEach(extension ->
                        recordAppliedDirectiveReferences(references, source, extension, extension.getDirectives()));
    }

    private void recordInputTypeReferences(Map<String, Map<String, NamedNode<?>>> references) {
        typeRegistry.getTypes(InputObjectTypeDefinition.class)
                .forEach(definition -> recordInputObjectReferences(references, definition));
        typeRegistry.inputObjectTypeExtensions().values()
                .forEach(extensions -> extensions.forEach(extension -> recordInputObjectReferences(references, extension)));
        typeRegistry.getTypes(EnumTypeDefinition.class)
                .forEach(definition -> recordEnumReferences(references, definition));
        typeRegistry.enumTypeExtensions().values()
                .forEach(extensions -> extensions.forEach(extension -> recordEnumReferences(references, extension)));
        typeRegistry.scalars().values()
                .forEach(definition -> recordScalarReferences(references, definition));
        typeRegistry.scalarTypeExtensions().values()
                .forEach(extensions -> extensions.forEach(extension -> recordScalarReferences(references, extension)));
    }

    private static void recordInputObjectReferences(Map<String, Map<String, NamedNode<?>>> references,
                                                    InputObjectTypeDefinition definition) {
        String source = typeKey(definition.getName());
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getInputValueDefinitions().forEach(field -> {
            recordAppliedDirectiveReferences(references, source, field, field.getDirectives());
            recordReference(references, source, typeKey(TypeUtil.unwrapAll(field.getType()).getName()), field);
        });
    }

    private static void recordEnumReferences(Map<String, Map<String, NamedNode<?>>> references,
                                             EnumTypeDefinition definition) {
        String source = typeKey(definition.getName());
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getEnumValueDefinitions().forEach(value ->
                recordAppliedDirectiveReferences(references, source, value, value.getDirectives()));
    }

    private static void recordScalarReferences(Map<String, Map<String, NamedNode<?>>> references,
                                               ScalarTypeDefinition definition) {
        recordAppliedDirectiveReferences(references, typeKey(definition.getName()), definition, definition.getDirectives());
    }

    private static void recordAppliedDirectiveReferences(Map<String, Map<String, NamedNode<?>>> references,
                                                         String source,
                                                         NamedNode<?> location,
                                                         List<Directive> directives) {
        directives.forEach(directive -> {
            if (!directive.getName().equals(source)) {
                recordReference(references, source, directive.getName(), location);
            }
        });
    }

    private static void recordReference(Map<String, Map<String, NamedNode<?>>> references,
                                        String source,
                                        String target,
                                        NamedNode<?> location) {
        references.computeIfAbsent(source, key -> new LinkedHashMap<>())
                .putIfAbsent(target, location);
    }

    private static String typeKey(String typeName) {
        return "type:" + typeName;
    }

    private static void checkIndirectDirectiveCycles(
            Map<String, DirectiveDefinition> directiveDefinitionsByName,
            Map<String, Map<String, NamedNode<?>>> referencesByName,
            List<GraphQLError> errors) {
        Set<String> checked = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String directiveName : directiveDefinitionsByName.keySet()) {
            checkIndirectDirectiveCycles(directiveName, directiveDefinitionsByName, referencesByName, checked, visiting, path, errors);
        }
    }

    private static void checkIndirectDirectiveCycles(String nodeName,
                                                     Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                     Map<String, Map<String, NamedNode<?>>> referencesByName,
                                                     Set<String> checked,
                                                     Set<String> visiting,
                                                     List<String> path,
                                                     List<GraphQLError> errors) {
        if (checked.contains(nodeName)) {
            return;
        }

        visiting.add(nodeName);
        path.add(nodeName);
        checkIndirectDirectiveCycleReferences(nodeName, directiveDefinitionsByName, referencesByName, checked, visiting, path, errors);
        path.remove(path.size() - 1);
        visiting.remove(nodeName);
        checked.add(nodeName);
    }

    private static void checkIndirectDirectiveCycleReferences(String nodeName,
                                                             Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                             Map<String, Map<String, NamedNode<?>>> referencesByName,
                                                             Set<String> checked,
                                                             Set<String> visiting,
                                                             List<String> path,
                                                             List<GraphQLError> errors) {
        Map<String, NamedNode<?>> references = referencesByName.getOrDefault(nodeName, Collections.emptyMap());
        for (Map.Entry<String, NamedNode<?>> entry : references.entrySet()) {
            checkIndirectDirectiveCycleReference(entry.getKey(), entry.getValue(), directiveDefinitionsByName, referencesByName, checked, visiting, path, errors);
        }
    }

    private static void checkIndirectDirectiveCycleReference(String referencedNodeName,
                                                            NamedNode<?> location,
                                                            Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                            Map<String, Map<String, NamedNode<?>>> referencesByName,
                                                            Set<String> checked,
                                                            Set<String> visiting,
                                                            List<String> path,
                                                            List<GraphQLError> errors) {
        if (visiting.contains(referencedNodeName)) {
            addIndirectDirectiveCycleError(referencedNodeName, location, directiveDefinitionsByName, path, errors);
            return;
        }
        if (!checked.contains(referencedNodeName)) {
            checkIndirectDirectiveCycles(referencedNodeName, directiveDefinitionsByName, referencesByName, checked, visiting, path, errors);
        }
    }

    private static void addIndirectDirectiveCycleError(String repeatedNodeName,
                                                       NamedNode<?> location,
                                                       Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                       List<String> path,
                                                       List<GraphQLError> errors) {
        List<String> cyclePath = directiveCyclePath(repeatedNodeName, path);
        String directiveName = cyclePath.stream()
                .filter(directiveDefinitionsByName::containsKey)
                .findFirst()
                .orElse(null);
        if (directiveName == null) {
            return;
        }
        cyclePath = rotateCyclePath(cyclePath, directiveName);
        String cyclePathString = cyclePath.stream()
                .map(SchemaTypeDirectivesChecker::displayNodeName)
                .collect(joining(" -> "));

        DirectiveDefinition directiveDefinition = assertNotNull(directiveDefinitionsByName.get(directiveName));
        errors.add(new DirectiveIllegalReferenceError(directiveDefinition, location, cyclePathString));
    }

    private static List<String> directiveCyclePath(String repeatedDirectiveName, List<String> path) {
        int cycleStart = path.indexOf(repeatedDirectiveName);
        List<String> cyclePath = new ArrayList<>(path.subList(cycleStart, path.size()));
        cyclePath.add(repeatedDirectiveName);
        return cyclePath;
    }

    private static List<String> rotateCyclePath(List<String> cyclePath, String firstNode) {
        List<String> nodes = cyclePath.subList(0, cyclePath.size() - 1);
        int firstNodeIndex = nodes.indexOf(firstNode);
        List<String> result = new ArrayList<>(nodes.size() + 1);
        result.addAll(nodes.subList(firstNodeIndex, nodes.size()));
        result.addAll(nodes.subList(0, firstNodeIndex));
        result.add(firstNode);
        return result;
    }

    private static String displayNodeName(String nodeName) {
        if (nodeName.startsWith("type:")) {
            return nodeName.substring("type:".length());
        }
        return nodeName;
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
