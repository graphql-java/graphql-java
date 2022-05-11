package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.AstPrinter;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.errors.InterfaceFieldArgumentNotOptionalError;
import graphql.schema.idl.errors.InterfaceFieldArgumentRedefinitionError;
import graphql.schema.idl.errors.InterfaceFieldRedefinitionError;
import graphql.schema.idl.errors.InterfaceImplementedMoreThanOnceError;
import graphql.schema.idl.errors.InterfaceImplementingItselfError;
import graphql.schema.idl.errors.InterfaceWithCircularImplementationHierarchyError;
import graphql.schema.idl.errors.MissingInterfaceFieldArgumentsError;
import graphql.schema.idl.errors.MissingInterfaceFieldError;
import graphql.schema.idl.errors.MissingTransitiveInterfaceError;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * A support class to help break up the large SchemaTypeChecker class. This handles
 * the checking of {@link graphql.language.ImplementingTypeDefinition}s.
 */
@Internal
class ImplementingTypesChecker {
    private static final Map<Class<? extends ImplementingTypeDefinition>, String> TYPE_OF_MAP = new HashMap<>();

    static {
        TYPE_OF_MAP.put(ObjectTypeDefinition.class, "object");
        TYPE_OF_MAP.put(ObjectTypeExtensionDefinition.class, "object extension");
        TYPE_OF_MAP.put(InterfaceTypeDefinition.class, "interface");
        TYPE_OF_MAP.put(InterfaceTypeExtensionDefinition.class, "interface extension");
    }

    /*
     * "Implementing types" (i.e.: types that might implement interfaces) have the potential to be invalid if incorrectly defined.
     *
     * The same interface might not be implemented more than once by a type and its extensions
     * The implementing type must implement all the transitive interfaces
     * An interface implementation must not result in a circular reference (i.e.: an interface implementing itself)
     * All fields declared by an interface have to be correctly declared by its implementing type, including the proper field arguments
     */
    void checkImplementingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        List<InterfaceTypeDefinition> interfaces = typeRegistry.getTypes(InterfaceTypeDefinition.class);
        List<ObjectTypeDefinition> objects = typeRegistry.getTypes(ObjectTypeDefinition.class);

        Stream.<ImplementingTypeDefinition<?>>concat(interfaces.stream(), objects.stream())
                .forEach(type -> checkImplementingType(errors, typeRegistry, type));
    }

    private void checkImplementingType(
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type) {

        Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces =
                checkInterfacesNotImplementedMoreThanOnce(errors, type, typeRegistry);

        checkInterfaceIsImplemented(errors, typeRegistry, type, implementedInterfaces);

        checkAncestorImplementation(errors, typeRegistry, type, implementedInterfaces);
    }

    private Map<InterfaceTypeDefinition, ImplementingTypeDefinition> checkInterfacesNotImplementedMoreThanOnce(
            List<GraphQLError> errors,
            ImplementingTypeDefinition type,
            TypeDefinitionRegistry typeRegistry
    ) {
        Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> implementedInterfaces =
                getLogicallyImplementedInterfaces(type, typeRegistry);

        Map<InterfaceTypeDefinition, ImplementingTypeDefinition> interfacesImplementedOnce = implementedInterfaces.entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() == 1)
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(0)
                ));

        implementedInterfaces.entrySet().stream()
                .filter(entry -> !interfacesImplementedOnce.containsKey(entry.getKey()))
                .forEach(entry -> {
                    entry.getValue().forEach(offendingType -> {
                        errors.add(new InterfaceImplementedMoreThanOnceError(TYPE_OF_MAP.get(offendingType.getClass()), offendingType, entry.getKey()));
                    });
                });

        return interfacesImplementedOnce;
    }

    private void checkAncestorImplementation(
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type,
            Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces) {

        if (implementedInterfaces.containsKey(type)) {
            errors.add(new InterfaceImplementingItselfError(TYPE_OF_MAP.get(type.getClass()), type));
            return;
        }

        implementedInterfaces.forEach((implementedInterface, implementingType) -> {
            Set<InterfaceTypeDefinition> transitiveInterfaces = getLogicallyImplementedInterfaces(implementedInterface, typeRegistry).keySet();

            transitiveInterfaces.forEach(transitiveInterface -> {
                if (transitiveInterface.equals(type)) {
                    errors.add(new InterfaceWithCircularImplementationHierarchyError(TYPE_OF_MAP.get(type.getClass()), type, implementedInterface));
                } else if (!implementedInterfaces.containsKey(transitiveInterface)) {
                    errors.add(new MissingTransitiveInterfaceError(TYPE_OF_MAP.get(implementingType.getClass()), implementingType, implementedInterface, transitiveInterface));
                }
            });
        });
    }

    private void checkInterfaceIsImplemented(
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type,
            Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces
    ) {
        Set<FieldDefinition> fieldDefinitions = getLogicallyDeclaredFields(type, typeRegistry);

        Map<String, FieldDefinition> typeFields = fieldDefinitions.stream()
                .collect(toMap(FieldDefinition::getName, Function.identity(), mergeFirstValue()));

        implementedInterfaces.forEach((implementedInterface, implementingType) -> {
            implementedInterface.getFieldDefinitions().forEach(interfaceFieldDef -> {
                FieldDefinition typeFieldDef = typeFields.get(interfaceFieldDef.getName());
                if (typeFieldDef == null) {
                    errors.add(new MissingInterfaceFieldError(TYPE_OF_MAP.get(implementingType.getClass()), implementingType, implementedInterface, interfaceFieldDef));
                } else {
                    if (!typeRegistry.isSubTypeOf(typeFieldDef.getType(), interfaceFieldDef.getType())) {
                        String interfaceFieldType = AstPrinter.printAst(interfaceFieldDef.getType());
                        String objectFieldType = AstPrinter.printAst(typeFieldDef.getType());
                        errors.add(new InterfaceFieldRedefinitionError(TYPE_OF_MAP.get(implementingType.getClass()), implementingType, implementedInterface, typeFieldDef, objectFieldType, interfaceFieldType));
                    }

                    // look at arguments
                    List<InputValueDefinition> objectArgs = typeFieldDef.getInputValueDefinitions();
                    List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
                    if (objectArgs.size() < interfaceArgs.size()) {
                        errors.add(new MissingInterfaceFieldArgumentsError(TYPE_OF_MAP.get(implementingType.getClass()), implementingType, implementedInterface, typeFieldDef));
                    } else {
                        checkArgumentConsistency(TYPE_OF_MAP.get(implementingType.getClass()), implementingType, implementedInterface, typeFieldDef, interfaceFieldDef, errors);
                    }
                }
            });
        });
    }

    private void checkArgumentConsistency(
            String typeOfType,
            ImplementingTypeDefinition objectTypeDef,
            InterfaceTypeDefinition interfaceTypeDef,
            FieldDefinition objectFieldDef,
            FieldDefinition interfaceFieldDef,
            List<GraphQLError> errors
    ) {
        Map<String, InputValueDefinition> objectArgs = FpKit.getByName(objectFieldDef.getInputValueDefinitions(), InputValueDefinition::getName);
        Map<String, InputValueDefinition> interfaceArgs = FpKit.getByName(interfaceFieldDef.getInputValueDefinitions(), InputValueDefinition::getName);
        for (Map.Entry<String, InputValueDefinition> interfaceEntries : interfaceArgs.entrySet()) {
            InputValueDefinition interfaceArg = interfaceEntries.getValue();
            InputValueDefinition objectArg = objectArgs.get(interfaceEntries.getKey());
            if (objectArg == null) {
                errors.add(new MissingInterfaceFieldArgumentsError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef));
            } else {
                String interfaceArgStr = AstPrinter.printAstCompact(interfaceArg);
                String objectArgStr = AstPrinter.printAstCompact(objectArg);
                if (!interfaceArgStr.equals(objectArgStr)) {
                    errors.add(new InterfaceFieldArgumentRedefinitionError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr, interfaceArgStr));
                }
            }
        }

        if (objectArgs.size() > interfaceArgs.size()) {
            for (Map.Entry<String, InputValueDefinition> objetEntries : objectArgs.entrySet()) {
                InputValueDefinition objectArg = objetEntries.getValue();
                InputValueDefinition interfaceArg = interfaceArgs.get(objetEntries.getKey());
                if (interfaceArg == null) {
                    // there is no interface counterpart previously checked above
                    if (objectArg.getType() instanceof NonNullType) {
                        String objectArgStr = AstPrinter.printAst(objectArg);
                        errors.add(new InterfaceFieldArgumentNotOptionalError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr));
                    }
                }
            }
        }
    }

    private Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> getLogicallyImplementedInterfaces(
            ImplementingTypeDefinition type,
            TypeDefinitionRegistry typeRegistry
    ) {

        Stream<ImplementingTypeDefinition> extensions = Stream.concat(
                typeRegistry.interfaceTypeExtensions().getOrDefault(type.getName(), emptyList()).stream(),
                typeRegistry.objectTypeExtensions().getOrDefault(type.getName(), emptyList()).stream()
        );

        return Stream.concat(Stream.of(type), extensions)
                .collect(HashMap::new, (map, implementingType) -> {
                    List<Type> implementedInterfaces = implementingType.getImplements();

                    toInterfaceTypeDefinitions(typeRegistry, implementedInterfaces).forEach(implemented -> {
                        List<ImplementingTypeDefinition> implementingTypes = map.getOrDefault(implemented, new ArrayList<>());
                        implementingTypes.add(implementingType);
                        map.put(implemented, implementingTypes);
                    });
                }, HashMap::putAll);

    }

    private Set<FieldDefinition> getLogicallyDeclaredFields(
            ImplementingTypeDefinition type,
            TypeDefinitionRegistry typeRegistry
    ) {

        Stream<ImplementingTypeDefinition> extensions = Stream.concat(
                typeRegistry.interfaceTypeExtensions().getOrDefault(type.getName(), emptyList()).stream(),
                typeRegistry.objectTypeExtensions().getOrDefault(type.getName(), emptyList()).stream()
        );

        return Stream.concat(Stream.of(type), extensions)
                .flatMap(implementingType -> {
                    List<FieldDefinition> fieldDefinitions = implementingType.getFieldDefinitions();
                    return fieldDefinitions.stream();
                })
                .collect(toSet());
    }

    private <T> BinaryOperator<T> mergeFirstValue() {
        return (v1, v2) -> v1;
    }

    private Optional<InterfaceTypeDefinition> toInterfaceTypeDefinition(Type type, TypeDefinitionRegistry typeRegistry) {
        TypeInfo typeInfo = TypeInfo.typeInfo(type);
        TypeName unwrapped = typeInfo.getTypeName();

        return typeRegistry.getType(unwrapped, InterfaceTypeDefinition.class);
    }

    private Set<InterfaceTypeDefinition> toInterfaceTypeDefinitions(TypeDefinitionRegistry typeRegistry, Collection<Type> implementsTypes) {
        return implementsTypes.stream()
                .map(t -> toInterfaceTypeDefinition(t, typeRegistry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toSet());
    }
}
