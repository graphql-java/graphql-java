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

/**
 * This is responsible for traversing EVERY type and field in the registry and ensuring that
 * any directives used follow the directive definition rules, for example
 * field directives can be used on object types
 */
@Internal
class SchemaTypeDirectivesChecker {

    private static final String TYPE_PREFIX = "type:";

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
        checkIndirectDirectiveCycles(directiveDefinitionsByName, errors);
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

    private Map<String, NamedNode<?>> directiveReferences(DirectiveDefinition definition) {
        Map<String, NamedNode<?>> references = new LinkedHashMap<>();
        String source = definition.getName();
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getInputValueDefinitions().forEach(argument -> {
            recordAppliedDirectiveReferences(references, source, argument, argument.getDirectives());
            recordReference(references, typeKey(TypeUtil.unwrapAll(argument.getType()).getName()), argument);
        });
        typeRegistry.directiveExtensions()
                .getOrDefault(source, Collections.emptyList())
                .forEach(extension ->
                        recordAppliedDirectiveReferences(references, source, extension, extension.getDirectives()));
        return references;
    }

    private Map<String, NamedNode<?>> typeReferences(String typeName) {
        Map<String, NamedNode<?>> references = new LinkedHashMap<>();
        TypeDefinition<?> definition = findTypeDefFromRegistry(typeName, typeRegistry);
        if (definition instanceof InputObjectTypeDefinition) {
            recordInputObjectReferences(references, (InputObjectTypeDefinition) definition);
        } else if (definition instanceof EnumTypeDefinition) {
            recordEnumReferences(references, (EnumTypeDefinition) definition);
        } else if (definition instanceof ScalarTypeDefinition) {
            recordScalarReferences(references, (ScalarTypeDefinition) definition);
        }

        typeRegistry.inputObjectTypeExtensions()
                .getOrDefault(typeName, Collections.emptyList())
                .forEach(extension -> recordInputObjectReferences(references, extension));
        typeRegistry.enumTypeExtensions()
                .getOrDefault(typeName, Collections.emptyList())
                .forEach(extension -> recordEnumReferences(references, extension));
        typeRegistry.scalarTypeExtensions()
                .getOrDefault(typeName, Collections.emptyList())
                .forEach(extension -> recordScalarReferences(references, extension));
        return references;
    }

    private static void recordInputObjectReferences(Map<String, NamedNode<?>> references,
                                                    InputObjectTypeDefinition definition) {
        String source = typeKey(definition.getName());
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getInputValueDefinitions().forEach(field -> {
            recordAppliedDirectiveReferences(references, source, field, field.getDirectives());
            recordReference(references, typeKey(TypeUtil.unwrapAll(field.getType()).getName()), field);
        });
    }

    private static void recordEnumReferences(Map<String, NamedNode<?>> references,
                                             EnumTypeDefinition definition) {
        String source = typeKey(definition.getName());
        recordAppliedDirectiveReferences(references, source, definition, definition.getDirectives());
        definition.getEnumValueDefinitions().forEach(value ->
                recordAppliedDirectiveReferences(references, source, value, value.getDirectives()));
    }

    private static void recordScalarReferences(Map<String, NamedNode<?>> references,
                                               ScalarTypeDefinition definition) {
        recordAppliedDirectiveReferences(references, typeKey(definition.getName()), definition, definition.getDirectives());
    }

    private static void recordAppliedDirectiveReferences(Map<String, NamedNode<?>> references,
                                                         String source,
                                                         NamedNode<?> location,
                                                         List<Directive> directives) {
        directives.forEach(directive -> {
            if (!directive.getName().equals(source)) {
                recordReference(references, directive.getName(), location);
            }
        });
    }

    private static void recordReference(Map<String, NamedNode<?>> references,
                                        String target,
                                        NamedNode<?> location) {
        references.putIfAbsent(target, location);
    }

    private static String typeKey(String typeName) {
        return TYPE_PREFIX + typeName;
    }

    private void checkIndirectDirectiveCycles(
            Map<String, DirectiveDefinition> directiveDefinitionsByName,
            List<GraphQLError> errors) {
        Set<String> checked = new LinkedHashSet<>();
        LinkedHashSet<String> currentPath = new LinkedHashSet<>();
        for (DirectiveDefinition directiveDefinition : directiveDefinitionsByName.values()) {
            checkDirectiveReferencesForCycles(directiveDefinition.getName(), directiveDefinitionsByName, checked, currentPath, errors);
        }
    }

    private void checkDirectiveReferencesForCycles(String nodeName,
                                                   Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                   Set<String> checked,
                                                   LinkedHashSet<String> currentPath,
                                                   List<GraphQLError> errors) {
        if (checked.contains(nodeName)) {
            return;
        }

        currentPath.add(nodeName);
        for (Map.Entry<String, NamedNode<?>> reference : referencesFor(nodeName, directiveDefinitionsByName).entrySet()) {
            String referencedNodeName = reference.getKey();
            if (currentPath.contains(referencedNodeName)) {
                addIndirectDirectiveCycleError(referencedNodeName, reference.getValue(), directiveDefinitionsByName, currentPath, errors);
                continue;
            }
            checkDirectiveReferencesForCycles(referencedNodeName, directiveDefinitionsByName, checked, currentPath, errors);
        }
        currentPath.remove(nodeName);
        checked.add(nodeName);
    }

    private Map<String, NamedNode<?>> referencesFor(
            String nodeName,
            Map<String, DirectiveDefinition> directiveDefinitionsByName) {
        if (nodeName.startsWith(TYPE_PREFIX)) {
            return typeReferences(nodeName.substring(TYPE_PREFIX.length()));
        }
        DirectiveDefinition definition = directiveDefinitionsByName.get(nodeName);
        if (definition == null) {
            return Collections.emptyMap();
        }
        return directiveReferences(definition);
    }

    private static void addIndirectDirectiveCycleError(String repeatedNodeName,
                                                       NamedNode<?> location,
                                                       Map<String, DirectiveDefinition> directiveDefinitionsByName,
                                                       LinkedHashSet<String> currentPath,
                                                       List<GraphQLError> errors) {
        List<String> path = new ArrayList<>(currentPath);
        List<String> cycle = path.subList(path.indexOf(repeatedNodeName), path.size());
        String directiveName = firstDirectiveName(cycle, directiveDefinitionsByName);
        if (directiveName == null) {
            return;
        }

        List<String> displayedCycle = rotateAndDisplayCycle(cycle, directiveName);
        String cyclePath = String.join(" -> ", displayedCycle);
        DirectiveDefinition directiveDefinition = assertNotNull(directiveDefinitionsByName.get(directiveName));
        errors.add(new DirectiveIllegalReferenceError(directiveDefinition, location, cyclePath));
    }

    private static String firstDirectiveName(
            List<String> cycle,
            Map<String, DirectiveDefinition> directiveDefinitionsByName) {
        for (String nodeName : cycle) {
            if (directiveDefinitionsByName.containsKey(nodeName)) {
                return nodeName;
            }
        }
        return null;
    }

    private static List<String> rotateAndDisplayCycle(List<String> cycle, String firstNode) {
        int firstNodeIndex = cycle.indexOf(firstNode);
        List<String> result = new ArrayList<>(cycle.size() + 1);
        for (int i = 0; i < cycle.size(); i++) {
            String nodeName = cycle.get((firstNodeIndex + i) % cycle.size());
            result.add(displayNodeName(nodeName));
        }
        result.add(displayNodeName(firstNode));
        return result;
    }

    private static String displayNodeName(String nodeName) {
        if (nodeName.startsWith(TYPE_PREFIX)) {
            return nodeName.substring(TYPE_PREFIX.length());
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
