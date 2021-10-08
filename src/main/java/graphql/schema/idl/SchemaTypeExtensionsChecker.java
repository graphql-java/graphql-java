package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.NonUniqueArgumentError;
import graphql.schema.idl.errors.NonUniqueNameError;
import graphql.schema.idl.errors.TypeExtensionEnumValueRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionFieldRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionMissingBaseTypeError;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static graphql.schema.idl.SchemaTypeChecker.checkNamedUniqueness;
import static graphql.util.FpKit.mergeFirst;

/**
 * A support class to help break up the large SchemaTypeChecker class.  This handles
 * the checking of "type extensions"
 */
@Internal
class SchemaTypeExtensionsChecker {

    void checkTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, DirectiveDefinition> directiveDefinitionMap = typeRegistry.getDirectiveDefinitions();
        checkObjectTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
        checkInterfaceTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
        checkUnionTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
        checkEnumTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
        checkScalarTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
        checkInputObjectTypeExtensions(errors, typeRegistry, directiveDefinitionMap);
    }


    /*
     * Object type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be an Object type.
     * The fields of an Object type extension must have unique names; no two fields may share the same name.
     * Any fields of an Object type extension must not be already defined on the original Object type.
     * Any directives provided must not already apply to the original Object type.
     * Any interfaces provided must not be already implemented by the original Object type.
     * The resulting extended object type must be a super-set of all interfaces it implements.
     */
    private void checkObjectTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.objectTypeExtensions()
                .forEach((name, extensions) -> {
                            checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, ObjectTypeDefinition.class);

                            extensions.forEach(extension -> {
                                List<FieldDefinition> fieldDefinitions = extension.getFieldDefinitions();
                                // field unique ness
                                checkNamedUniqueness(errors, extension.getFieldDefinitions(), FieldDefinition::getName,
                                        (namedField, fieldDef) -> new NonUniqueNameError(extension, fieldDef));

                                // field arg unique ness
                                extension.getFieldDefinitions().forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                                        (namedField, inputValueDefinition) -> new NonUniqueArgumentError(extension, fld, name)));

                                // directive checks
                                fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive ->
                                        checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                                                (argumentName, argument) -> new NonUniqueArgumentError(extension, fld, argumentName))));

                                //
                                // fields must be unique within a type extension
                                forEachBut(extension, extensions,
                                        otherTypeExt -> checkForFieldRedefinition(errors, otherTypeExt, otherTypeExt.getFieldDefinitions(), fieldDefinitions));

                                //
                                // then check for field re-defs from the base type
                                Optional<ObjectTypeDefinition> baseTypeOpt = typeRegistry.getType(extension.getName(), ObjectTypeDefinition.class);
                                baseTypeOpt.ifPresent(baseTypeDef -> checkForFieldRedefinition(errors, extension, fieldDefinitions, baseTypeDef.getFieldDefinitions()));

                            });
                        }
                );
    }

    /*
     * Interface type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be an Interface type.
     * The fields of an Interface type extension must have unique names; no two fields may share the same name.
     * Any fields of an Interface type extension must not be already defined on the original Interface type.
     * Any Object type which implemented the original Interface type must also be a super-set of the fields of the Interface type extension (which may be due to Object type extension).
     * Any directives provided must not already apply to the original Interface type.
     */
    private void checkInterfaceTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.interfaceTypeExtensions()
                .forEach((name, extensions) -> {
                    checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, InterfaceTypeDefinition.class);

                    extensions.forEach(extension -> {
                        List<FieldDefinition> fieldDefinitions = extension.getFieldDefinitions();
                        // field unique ness
                        checkNamedUniqueness(errors, extension.getFieldDefinitions(), FieldDefinition::getName,
                                (namedField, fieldDef) -> new NonUniqueNameError(extension, fieldDef));

                        // field arg unique ness
                        extension.getFieldDefinitions().forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                                (namedField, inputValueDefinition) -> new NonUniqueArgumentError(extension, fld, name)));

                        // directive checks
                        fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive ->
                                checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                                        (argumentName, argument) -> new NonUniqueArgumentError(extension, fld, argumentName))));

                        //
                        // fields must be unique within a type extension
                        forEachBut(extension, extensions,
                                otherTypeExt -> checkForFieldRedefinition(errors, otherTypeExt, otherTypeExt.getFieldDefinitions(), fieldDefinitions));

                        //
                        // then check for field re-defs from the base type
                        Optional<InterfaceTypeDefinition> baseTypeOpt = typeRegistry.getType(extension.getName(), InterfaceTypeDefinition.class);
                        baseTypeOpt.ifPresent(baseTypeDef -> checkForFieldRedefinition(errors, extension, fieldDefinitions, baseTypeDef.getFieldDefinitions()));
                    });
                });
    }

    /*
     * Union type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be a Union type.
     * The member types of a Union type extension must all be Object base types; Scalar, Interface and Union types must not be member types of a Union. Similarly, wrapping types must not be member types of a Union.
     * All member types of a Union type extension must be unique.
     * All member types of a Union type extension must not already be a member of the original Union type.
     * Any directives provided must not already apply to the original Union type.
     */
    private void checkUnionTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.unionTypeExtensions()
                .forEach((name, extensions) -> {
                    checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, UnionTypeDefinition.class);

                    extensions.forEach(extension -> {
                        List<TypeName> memberTypes = extension.getMemberTypes().stream()
                                .map(t -> TypeInfo.typeInfo(t).getTypeName()).collect(Collectors.toList());

                        checkNamedUniqueness(errors, memberTypes, TypeName::getName,
                                (namedMember, memberType) -> new NonUniqueNameError(extension, namedMember));

                        memberTypes.forEach(
                                memberType -> {
                                    Optional<ObjectTypeDefinition> unionTypeDefinition = typeRegistry.getType(memberType, ObjectTypeDefinition.class);
                                    if (!unionTypeDefinition.isPresent()) {
                                        errors.add(new MissingTypeError("union member", extension, memberType));
                                    }
                                }
                        );
                    });
                });
    }

    /*
     * Enum type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be an Enum type.
     * All values of an Enum type extension must be unique.
     * All values of an Enum type extension must not already be a value of the original Enum.
     * Any directives provided must not already apply to the original Enum type.
     */
    private void checkEnumTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.enumTypeExtensions()
                .forEach((name, extensions) -> {
                    checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, EnumTypeDefinition.class);

                    extensions.forEach(extension -> {
                        // field unique ness
                        List<EnumValueDefinition> enumValueDefinitions = extension.getEnumValueDefinitions();
                        checkNamedUniqueness(errors, enumValueDefinitions, EnumValueDefinition::getName,
                                (namedField, enumValue) -> new NonUniqueNameError(extension, enumValue));

                        //
                        // enum values must be unique within a type extension
                        forEachBut(extension, extensions,
                                otherTypeExt -> checkForEnumValueRedefinition(errors, otherTypeExt, otherTypeExt.getEnumValueDefinitions(), enumValueDefinitions));

                        //
                        // then check for field re-defs from the base type
                        Optional<EnumTypeDefinition> baseTypeOpt = typeRegistry.getType(extension.getName(), EnumTypeDefinition.class);
                        baseTypeOpt.ifPresent(baseTypeDef -> checkForEnumValueRedefinition(errors, extension, enumValueDefinitions, baseTypeDef.getEnumValueDefinitions()));

                    });


                });
    }

    /*
     * Scalar type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be a Scalar type.
     * Any directives provided must not already apply to the original Scalar type.
     */

    private void checkScalarTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.scalarTypeExtensions()
                .forEach((name, extensions) -> {
                    checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, ScalarTypeDefinition.class);
                });

    }

    /*
     * Input object type extensions have the potential to be invalid if incorrectly defined.
     *
     * The named type must already be defined and must be a Input Object type.
     * All fields of an Input Object type extension must have unique names.
     * All fields of an Input Object type extension must not already be a field of the original Input Object.
     * Any directives provided must not already apply to the original Input Object type.
     */
    private void checkInputObjectTypeExtensions(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Map<String, DirectiveDefinition> directiveDefinitionMap) {
        typeRegistry.inputObjectTypeExtensions()
                .forEach((name, extensions) -> {
                    checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, InputObjectTypeDefinition.class);
                    // field redefinitions
                    extensions.forEach(extension -> {
                        List<InputValueDefinition> inputValueDefinitions = extension.getInputValueDefinitions();
                        // field unique ness
                        checkNamedUniqueness(errors, inputValueDefinitions, InputValueDefinition::getName,
                                (namedField, fieldDef) -> new NonUniqueNameError(extension, fieldDef));

                        // directive checks
                        inputValueDefinitions.forEach(fld -> fld.getDirectives().forEach(directive ->
                                checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                                        (argumentName, argument) -> new NonUniqueArgumentError(extension, fld, argumentName))));
                        //
                        // fields must be unique within a type extension
                        forEachBut(extension, extensions,
                                otherTypeExt -> checkForInputValueRedefinition(errors, otherTypeExt, otherTypeExt.getInputValueDefinitions(), inputValueDefinitions));

                        //
                        // then check for field re-defs from the base type
                        Optional<InputObjectTypeDefinition> baseTypeOpt = typeRegistry.getType(extension.getName(), InputObjectTypeDefinition.class);
                        baseTypeOpt.ifPresent(baseTypeDef -> checkForInputValueRedefinition(errors, extension, inputValueDefinitions, baseTypeDef.getInputValueDefinitions()));
                    });

                });
    }


    private void checkTypeExtensionHasCorrespondingType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, String name, List<? extends TypeDefinition> extTypeList, Class<? extends TypeDefinition> targetClass) {
        TypeDefinition extensionDefinition = extTypeList.get(0);
        Optional<? extends TypeDefinition> typeDefinition = typeRegistry.getType(TypeName.newTypeName().name(name).build(), targetClass);
        if (!typeDefinition.isPresent()) {
            errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
        }
    }

    @SuppressWarnings("unchecked")

    private void checkForFieldRedefinition(List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fieldDefinitions, List<FieldDefinition> referenceFieldDefinitions) {

        Map<String, FieldDefinition> referenceMap = FpKit.getByName(referenceFieldDefinitions, FieldDefinition::getName, mergeFirst());

        fieldDefinitions.forEach(fld -> {
            if (referenceMap.containsKey(fld.getName())) {
                errors.add(new TypeExtensionFieldRedefinitionError(typeDefinition, fld));
            }
        });
    }

    private void checkForInputValueRedefinition(List<GraphQLError> errors, InputObjectTypeExtensionDefinition typeDefinition, List<InputValueDefinition> inputValueDefinitions, List<InputValueDefinition> referenceInputValues) {
        Map<String, InputValueDefinition> referenceMap = FpKit.getByName(referenceInputValues, InputValueDefinition::getName, mergeFirst());

        inputValueDefinitions.forEach(fld -> {
            if (referenceMap.containsKey(fld.getName())) {
                errors.add(new TypeExtensionFieldRedefinitionError(typeDefinition, fld));
            }
        });
    }

    private void checkForEnumValueRedefinition(List<GraphQLError> errors, TypeDefinition typeDefinition, List<EnumValueDefinition> enumValueDefinitions, List<EnumValueDefinition> referenceEnumValueDefinitions) {

        Map<String, EnumValueDefinition> referenceMap = FpKit.getByName(referenceEnumValueDefinitions, EnumValueDefinition::getName, mergeFirst());

        enumValueDefinitions.forEach(fld -> {
            if (referenceMap.containsKey(fld.getName())) {
                errors.add(new TypeExtensionEnumValueRedefinitionError(typeDefinition, fld));
            }
        });
    }

    private <T> void forEachBut(T butThisOne, List<T> list, Consumer<T> consumer) {
        for (T t : list) {
            if (t == butThisOne) {
                continue;
            }
            consumer.accept(t);
        }
    }
}
