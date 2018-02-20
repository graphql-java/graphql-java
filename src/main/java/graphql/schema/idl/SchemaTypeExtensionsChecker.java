package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.AstPrinter;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.TypeExtensionFieldRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionMissingBaseTypeError;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A support class to help break up the large SchemaTypeChecker class.  This handles
 * the checking of "type extensions"
 */
@Internal
class SchemaTypeExtensionsChecker {

    void checkTypeExtensionsHaveCorrespondingType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        typeRegistry.typeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, ObjectTypeDefinition.class));
        typeRegistry.interfaceTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, InterfaceTypeDefinition.class));
        typeRegistry.unionTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, UnionTypeDefinition.class));
        typeRegistry.enumTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, EnumTypeDefinition.class));
        typeRegistry.scalarTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, ScalarTypeDefinition.class));
        typeRegistry.inputObjectTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, InputObjectTypeDefinition.class));
    }

    private void checkTypeExtensionHasCorrespondingType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, String name, List<? extends TypeDefinition> extTypeList, Class targetClass) {
        TypeDefinition extensionDefinition = extTypeList.get(0);
        Optional<TypeDefinition> typeDefinition = typeRegistry.getType(new TypeName(name));
        if (!typeDefinition.isPresent()) {
            errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
        } else {
            if (!(typeDefinition.get().getClass().equals(targetClass))) {
                errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
            }
        }
    }

    /*
        A type can re-define a field if its actual the same type, but if they make 'fieldA : String' into
        'fieldA : Int' then we cant handle that.  Even 'fieldA : String' to 'fieldA: String!' is tough to handle
        so we don't
    */
    void checkTypeExtensionsFieldRedefinition(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        // object type  extensions
        Map<String, List<TypeExtensionDefinition>> typeExtensions = typeRegistry.typeExtensions();
        typeExtensions.values().forEach(extList -> extList.forEach(extension -> {
            //
            // first check for field re-defs within a type ext
            for (TypeExtensionDefinition otherTypeExt : extList) {
                if (otherTypeExt == extension) {
                    continue;
                }
                // its the children that matter - the fields cannot be redefined
                checkForFieldRedefinition(errors, otherTypeExt, otherTypeExt.getFieldDefinitions(), extension.getFieldDefinitions());
            }
            //
            // then check for field re-defs from the base type
            Optional<TypeDefinition> type = typeRegistry.getType(extension.getName());
            if (type.isPresent() && type.get() instanceof ObjectTypeDefinition) {
                ObjectTypeDefinition baseType = (ObjectTypeDefinition) type.get();

                checkForFieldRedefinition(errors, extension, extension.getFieldDefinitions(), baseType.getFieldDefinitions());
            }
        }));

        // interface extensions
        Map<String, List<InterfaceTypeExtensionDefinition>> interfaceTypeExtensions = typeRegistry.interfaceTypeExtensions();
        interfaceTypeExtensions.values().forEach(extList -> extList.forEach(extension -> {
            //
            // first check for field re-defs within a type ext
            for (InterfaceTypeExtensionDefinition otherTypeExt : extList) {
                if (otherTypeExt == extension) {
                    continue;
                }
                // its the children that matter - the fields cannot be redefined
                checkForFieldRedefinition(errors, otherTypeExt, otherTypeExt.getFieldDefinitions(), extension.getFieldDefinitions());
            }
            //
            // then check for field re-defs from the base type
            Optional<TypeDefinition> type = typeRegistry.getType(extension.getName());
            if (type.isPresent() && type.get() instanceof InterfaceTypeDefinition) {
                InterfaceTypeDefinition baseType = (InterfaceTypeDefinition) type.get();

                checkForFieldRedefinition(errors, extension, extension.getFieldDefinitions(), baseType.getFieldDefinitions());
            }
        }));

        // input object extensions
        Map<String, List<InputObjectTypeExtensionDefinition>> inputObjectTypeExtensions = typeRegistry.inputObjectTypeExtensions();
        inputObjectTypeExtensions.values().forEach(extList -> extList.forEach(extension -> {
            //
            // first check for field re-defs within a type ext
            for (InputObjectTypeExtensionDefinition otherTypeExt : extList) {
                if (otherTypeExt == extension) {
                    continue;
                }
                // its the children that matter - the fields cannot be redefined
                checkForInputValueRedefinition(errors, otherTypeExt, otherTypeExt.getInputValueDefinitions(), extension.getInputValueDefinitions());
            }
            //
            // then check for field re-defs from the base type
            Optional<TypeDefinition> type = typeRegistry.getType(extension.getName());
            if (type.isPresent() && type.get() instanceof InputObjectTypeExtensionDefinition) {
                InputObjectTypeExtensionDefinition baseType = (InputObjectTypeExtensionDefinition) type.get();

                checkForInputValueRedefinition(errors, extension, extension.getInputValueDefinitions(), baseType.getInputValueDefinitions());
            }
        }));

    }

    private void checkForFieldRedefinition(List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fieldDefinitions, List<FieldDefinition> referenceFieldDefinitions) {
        Map<String, FieldDefinition> referenceFields = referenceFieldDefinitions.stream()
                .collect(Collectors.toMap(
                        FieldDefinition::getName, Function.identity(), mergeFirstValue()
                ));

        fieldDefinitions.forEach(fld -> {
            FieldDefinition referenceField = referenceFields.get(fld.getName());
            if (referenceFields.containsKey(fld.getName())) {
                // ok they have the same field but is it the same type
                if (!isSameType(fld.getType(), referenceField.getType())) {
                    errors.add(new TypeExtensionFieldRedefinitionError(typeDefinition, fld));
                }
            }
        });
    }

    private void checkForInputValueRedefinition(List<GraphQLError> errors, InputObjectTypeExtensionDefinition typeDefinition, List<InputValueDefinition> inputValueDefinitions, List<InputValueDefinition> referenceInputValues) {
        Map<String, InputValueDefinition> referenceFields = referenceInputValues.stream()
                .collect(Collectors.toMap(
                        InputValueDefinition::getName, Function.identity(), mergeFirstValue()
                ));

        inputValueDefinitions.forEach(fld -> {
            InputValueDefinition referenceField = referenceFields.get(fld.getName());
            if (referenceFields.containsKey(fld.getName())) {
                // ok they have the same field but is it the same type
                if (!isSameType(fld.getType(), referenceField.getType())) {
                    errors.add(new TypeExtensionFieldRedefinitionError(typeDefinition, fld));
                }
            }
        });
    }

    private boolean isSameType(Type type1, Type type2) {
        String s1 = AstPrinter.printAst(type1);
        String s2 = AstPrinter.printAst(type2);
        return s1.equals(s2);
    }

    private <T> BinaryOperator<T> mergeFirstValue() {
        return (v1, v2) -> v1;
    }
}
