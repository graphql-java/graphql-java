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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

/**
 * A support class to help break up the large SchemaTypeChecker class.  This handles
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

    void checkImplementingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        final List<InterfaceTypeDefinition> interfaces = typeRegistry.getTypes(InterfaceTypeDefinition.class);
        final List<ObjectTypeDefinition> objects = typeRegistry.getTypes(ObjectTypeDefinition.class);

        Stream.of(
                interfaces.stream(),
                objects.stream()
        )
                .flatMap(Function.identity())
                .forEach(type -> checkImplementingType(TYPE_OF_MAP.get(type.getClass()), errors, typeRegistry, type));

    }

    private void checkImplementingType(
            String typeOfType,
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type) {

        Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces =
                checkInterfacesNotImplementedMoreThanOnce(errors, type, typeRegistry);

        checkInterfaceIsImplemented(typeOfType, typeRegistry, errors, type, implementedInterfaces);

        checkAncestorImplementation(typeOfType, errors, typeRegistry, type, implementedInterfaces);
    }

    private Map<InterfaceTypeDefinition, ImplementingTypeDefinition> checkInterfacesNotImplementedMoreThanOnce(List<GraphQLError> errors, ImplementingTypeDefinition type, TypeDefinitionRegistry typeRegistry) {
        Map<InterfaceTypeDefinition, List<ImplementingTypeDefinition>> implementedInterfaces = getLogicallyImplementedInterfaces(type, typeRegistry);

        Map<InterfaceTypeDefinition, ImplementingTypeDefinition> interfacesImplementedOnce = implementedInterfaces.entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() == 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(0)
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
            String typeOfType,
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type,
            Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces) {

        if (implementedInterfaces.containsKey(type)) {
            errors.add(new InterfaceImplementingItselfError(typeOfType, type));
            return;
        }

        implementedInterfaces.keySet().forEach(implementedInterface -> {
            Set<InterfaceTypeDefinition> transitiveInterfaces = getLogicallyImplementedInterfaces(implementedInterface, typeRegistry).keySet();

            transitiveInterfaces.forEach(transitiveInterface -> {
                if (transitiveInterface.equals(type)) {
                    errors.add(new InterfaceWithCircularImplementationHierarchyError(typeOfType, type, implementedInterface));
                } else if (!implementedInterfaces.containsKey(transitiveInterface)) {
                    ImplementingTypeDefinition offendingType = implementedInterfaces.get(implementedInterface);

                    errors.add(new MissingTransitiveInterfaceError(TYPE_OF_MAP.get(offendingType.getClass()), offendingType, implementedInterface, transitiveInterface));
                }
            });
        });
    }

    private void checkInterfaceIsImplemented(
            String typeOfType,
            TypeDefinitionRegistry typeRegistry,
            List<GraphQLError> errors,
            ImplementingTypeDefinition type,
            Map<InterfaceTypeDefinition, ImplementingTypeDefinition> implementedInterfaces
    ) {
        implementedInterfaces.entrySet().forEach(entry -> {
            InterfaceTypeDefinition implementedInterface = entry.getKey();

            Set<FieldDefinition> fieldDefinitions = getLogicallyDeclaredFields(type, typeRegistry);

            Map<String, FieldDefinition> objectFields = fieldDefinitions.stream()
                    .collect(Collectors.toMap(
                            FieldDefinition::getName, Function.identity(), mergeFirstValue()
                    ));

            implementedInterface.getFieldDefinitions().forEach(interfaceFieldDef -> {
                FieldDefinition objectFieldDef = objectFields.get(interfaceFieldDef.getName());
                if (objectFieldDef == null) {
                    ImplementingTypeDefinition offendingType = entry.getValue();
                    errors.add(new MissingInterfaceFieldError(TYPE_OF_MAP.get(offendingType.getClass()), offendingType, implementedInterface, interfaceFieldDef));
                } else {
                    if (!typeRegistry.isSubTypeOf(objectFieldDef.getType(), interfaceFieldDef.getType())) {
                        String interfaceFieldType = AstPrinter.printAst(interfaceFieldDef.getType());
                        String objectFieldType = AstPrinter.printAst(objectFieldDef.getType());
                        ImplementingTypeDefinition offendingType = entry.getValue();
//                        errors.add(new InterfaceFieldRedefinitionError(TYPE_OF_MAP.get(offendingType.getClass()), offendingType, implementedInterface, objectFieldDef, objectFieldType, interfaceFieldType));
                    }

                    // look at arguments
                    List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
                    List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
                    if (objectArgs.size() < interfaceArgs.size()) {
//                        errors.add(new MissingInterfaceFieldArgumentsError(typeOfType, type, implementedInterface, objectFieldDef));
                    } else {
                        checkArgumentConsistency(typeOfType, type, implementedInterface, objectFieldDef, interfaceFieldDef, errors);
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
        List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
        List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
        for (int i = 0; i < interfaceArgs.size(); i++) {
            InputValueDefinition interfaceArg = interfaceArgs.get(i);
            InputValueDefinition objectArg = objectArgs.get(i);
            String interfaceArgStr = AstPrinter.printAstCompact(interfaceArg);
            String objectArgStr = AstPrinter.printAstCompact(objectArg);
            if (!interfaceArgStr.equals(objectArgStr)) {
//                errors.add(new InterfaceFieldArgumentRedefinitionError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr, interfaceArgStr));
            }
        }

        if (objectArgs.size() > interfaceArgs.size()) {
            for (int i = interfaceArgs.size(); i < objectArgs.size(); i++) {
                InputValueDefinition objectArg = objectArgs.get(i);
                if (objectArg.getType() instanceof NonNullType) {
                    String objectArgStr = AstPrinter.printAst(objectArg);
//                    errors.add(new InterfaceFieldArgumentNotOptionalError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr));
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
        final Stream<FieldDefinition> directFields = type.getFieldDefinitions().stream();

        final Stream<FieldDefinition> extensionFields = Stream.<ImplementingTypeDefinition>concat(
                typeRegistry.interfaceTypeExtensions().getOrDefault(type.getName(), emptyList()).stream(),
                typeRegistry.objectTypeExtensions().getOrDefault(type.getName(), emptyList()).stream()
        )
                .map(ImplementingTypeDefinition::getFieldDefinitions)
                .flatMap(Collection::stream);

        return Stream.concat(directFields, extensionFields).collect(toSet());
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
