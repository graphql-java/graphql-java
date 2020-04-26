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
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.errors.InterfaceFieldArgumentNotOptionalError;
import graphql.schema.idl.errors.InterfaceFieldArgumentRedefinitionError;
import graphql.schema.idl.errors.InterfaceFieldRedefinitionError;
import graphql.schema.idl.errors.MissingInterfaceFieldArgumentsError;
import graphql.schema.idl.errors.MissingInterfaceFieldError;
import graphql.schema.idl.errors.MissingTransitiveInterfaceError;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * A support class to help break up the large SchemaTypeChecker class.  This handles
 * the checking of {@link graphql.language.ImplementingTypeDefinition}s.
 */
@Internal
class ImplementingTypesChecker {

    void checkImplementingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(type -> {
            checkImplementingType(errors, typeRegistry, type, "object");
        });


        List<ObjectTypeExtensionDefinition> objectExtensions = typeRegistry.objectTypeExtensions().values()
                .stream().flatMap(Collection::stream).collect(toList());

        objectExtensions.forEach(type -> {
            checkImplementingType(errors, typeRegistry, type, "object extension");
        });

        // interfaces
        List<InterfaceTypeDefinition> interfacesTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfacesTypes.forEach(type -> {
            checkImplementingType(errors, typeRegistry, type, "interface");
        });

        List<InterfaceTypeExtensionDefinition> interfaceExtensions = typeRegistry.interfaceTypeExtensions().values()
                .stream().flatMap(Collection::stream).collect(toList());

        interfaceExtensions.forEach(type -> {
            checkImplementingType(errors, typeRegistry, type, "interface extension");
        });
    }

    private void checkImplementingType(
            List<GraphQLError> errors,
            TypeDefinitionRegistry typeRegistry,
            ImplementingTypeDefinition type,
            String typeName) {

        List<Type> implementsTypes = type.getImplements();
        implementsTypes.forEach(checkInterfaceIsImplemented(typeName, typeRegistry, errors, type));

        checkTransitiveInterfacesAreDeclared(typeName, typeRegistry, errors, type, implementsTypes);
    }


    private Consumer<? super Type> checkInterfaceIsImplemented(String typeOfType, TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, ImplementingTypeDefinition typeDefinition) {
        return t -> {
            List<FieldDefinition> fieldDefinitions = typeDefinition.getFieldDefinitions();

            // previous checks handle the missing case and wrong type case
            toInterfaceTypeDefinition(t, typeRegistry)
                    .ifPresent(interfaceTypeDef -> {
                        Map<String, FieldDefinition> objectFields = fieldDefinitions.stream()
                                .collect(Collectors.toMap(
                                        FieldDefinition::getName, Function.identity(), mergeFirstValue()
                                ));

                        interfaceTypeDef.getFieldDefinitions().forEach(interfaceFieldDef -> {
                            FieldDefinition objectFieldDef = objectFields.get(interfaceFieldDef.getName());
                            if (objectFieldDef == null) {
                                errors.add(new MissingInterfaceFieldError(typeOfType, typeDefinition, interfaceTypeDef, interfaceFieldDef));
                            } else {
                                if (!typeRegistry.isSubTypeOf(objectFieldDef.getType(), interfaceFieldDef.getType())) {
                                    String interfaceFieldType = AstPrinter.printAst(interfaceFieldDef.getType());
                                    String objectFieldType = AstPrinter.printAst(objectFieldDef.getType());
                                    errors.add(new InterfaceFieldRedefinitionError(typeOfType, typeDefinition, interfaceTypeDef, objectFieldDef, objectFieldType, interfaceFieldType));
                                }

                                // look at arguments
                                List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
                                List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
                                if (objectArgs.size() < interfaceArgs.size()) {
                                    errors.add(new MissingInterfaceFieldArgumentsError(typeOfType, typeDefinition, interfaceTypeDef, objectFieldDef));
                                } else {
                                    checkArgumentConsistency(typeOfType, typeDefinition, interfaceTypeDef, objectFieldDef, interfaceFieldDef, errors);
                                }
                            }
                        });
                    });
        };
    }

    private void checkTransitiveInterfacesAreDeclared(
            String typeOfType,
            TypeDefinitionRegistry typeRegistry,
            List<GraphQLError> errors,
            ImplementingTypeDefinition typeDefinition,
            List<Type> implementsInterfaces
    ) {
        Set<String> interfacesToBeImplemented = new HashSet<>();

        findImplementingInterfaces(typeDefinition, typeRegistry, interfacesToBeImplemented);

        Set<String> implementsInterfacesNames = implementsInterfaces.stream()
                .map(t -> toInterfaceTypeDefinition(t, typeRegistry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(TypeDefinition::getName)
                .collect(Collectors.toSet());

        Set<String> missingInterfaces = interfacesToBeImplemented.stream()
                .filter(i -> !implementsInterfacesNames.contains(i))
                .collect(Collectors.toSet());

        if (!missingInterfaces.isEmpty()) {
            errors.add(new MissingTransitiveInterfaceError(typeOfType, typeDefinition, missingInterfaces));
        }
    }

    private void checkArgumentConsistency(String typeOfType, TypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, FieldDefinition interfaceFieldDef, List<GraphQLError> errors) {
        List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
        List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
        for (int i = 0; i < interfaceArgs.size(); i++) {
            InputValueDefinition interfaceArg = interfaceArgs.get(i);
            InputValueDefinition objectArg = objectArgs.get(i);
            String interfaceArgStr = AstPrinter.printAstCompact(interfaceArg);
            String objectArgStr = AstPrinter.printAstCompact(objectArg);
            if (!interfaceArgStr.equals(objectArgStr)) {
                errors.add(new InterfaceFieldArgumentRedefinitionError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr, interfaceArgStr));
            }
        }

        if (objectArgs.size() > interfaceArgs.size()) {
            for (int i = interfaceArgs.size(); i < objectArgs.size(); i++) {
                InputValueDefinition objectArg = objectArgs.get(i);
                if (objectArg.getType() instanceof NonNullType) {
                    String objectArgStr = AstPrinter.printAst(objectArg);
                    errors.add(new InterfaceFieldArgumentNotOptionalError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr));
                }
            }
        }
    }

    private <T extends TypeDefinition> List<T> filterTo(Map<String, TypeDefinition> types, Class<? extends T> clazz) {
        return types.values().stream()
                .filter(t -> clazz.equals(t.getClass()))
                .map(clazz::cast)
                .collect(toList());
    }

    private <T> BinaryOperator<T> mergeFirstValue() {
        return (v1, v2) -> v1;
    }

    private void findImplementingInterfaces(ImplementingTypeDefinition type, TypeDefinitionRegistry typeRegistry, Set<String> allInterfaces) {
        final List<Type> implementsTypes = type.getImplements();

        implementsTypes.stream()
                .map(t -> toInterfaceTypeDefinition(t, typeRegistry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(t -> {
                    allInterfaces.add(t.getName());
                    findImplementingInterfaces(t, typeRegistry, allInterfaces);
                });
    }

    private Optional<InterfaceTypeDefinition> toInterfaceTypeDefinition(Type type, TypeDefinitionRegistry typeRegistry) {
        TypeInfo typeInfo = TypeInfo.typeInfo(type);
        TypeName unwrapped = typeInfo.getTypeName();

        return typeRegistry.getType(unwrapped, InterfaceTypeDefinition.class);
    }

}
