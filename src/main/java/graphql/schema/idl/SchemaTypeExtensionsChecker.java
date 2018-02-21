package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.AstPrinter;
import graphql.language.Directive;
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
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.TypeExtensionDirectiveRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionFieldRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionMissingBaseTypeError;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static graphql.util.FpKit.mergeFirst;

/**
 * A support class to help break up the large SchemaTypeChecker class.  This handles
 * the checking of "type extensions"
 *
 * From the spec:
 *
 * Interface type extensions have the potential to be invalid if incorrectly defined.
 *
 * The named type must already be defined and must be an Interface type.
 * The fields of an Interface type extension must have unique names; no two fields may share the same name.
 * Any fields of an Interface type extension must not be already defined on the original Interface type.
 * Any Object type which implemented the original Interface type must also be a super-set of the fields of the Interface type extension (which may be due to Object type extension).
 * Any directives provided must not already apply to the original Interface type.
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
                .forEach((name, extensions) -> checkUnionTypeExtension(errors, typeRegistry, name, extensions));
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

    private void checkTypeExtensionHasCorrespondingType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, String name, List<? extends TypeDefinition> extTypeList, Class<? extends TypeDefinition> targetClass) {
        TypeDefinition extensionDefinition = extTypeList.get(0);
        Optional<? extends TypeDefinition> typeDefinition = typeRegistry.getType(new TypeName(name), targetClass);
        if (!typeDefinition.isPresent()) {
            errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
        }
    }

    void checkTypeExtensionsDirectiveRedefinition(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        typeRegistry.typeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, ObjectTypeDefinition.class));
        typeRegistry.interfaceTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, InterfaceTypeDefinition.class));
        typeRegistry.unionTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, UnionTypeDefinition.class));
        typeRegistry.enumTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, EnumTypeDefinition.class));
        typeRegistry.scalarTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, ScalarTypeDefinition.class));
        typeRegistry.inputObjectTypeExtensions()
                .forEach((name, extensions) ->
                        checkTypeExtensionDirectiveRedefinition(errors, typeRegistry, name, extensions, InputObjectTypeDefinition.class));
    }

    @SuppressWarnings("unchecked")
    private void checkTypeExtensionDirectiveRedefinition(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, String name, List<? extends TypeDefinition> extensions, Class<? extends TypeDefinition> targetClass) {
        TypeDefinition extensionDefinition = extensions.get(0);
        Optional<? extends TypeDefinition> typeDefinition = typeRegistry.getType(new TypeName(name), targetClass);
        if (typeDefinition.isPresent() && typeDefinition.get().getClass().equals(targetClass)) {
            List<Directive> directives = typeDefinition.get().getDirectives();
            Map<String, Directive> directiveMap = FpKit.getByName(directives, Directive::getName, mergeFirst());
            extensions.forEach(typeExt -> {
                        List<Directive> extDirectives = typeExt.getDirectives();
                        extDirectives.forEach(directive -> {
                            if (directiveMap.containsKey(directive.getName())) {
                                errors.add(new TypeExtensionDirectiveRedefinitionError(typeDefinition.get(), directive));
                            }
                        });
                    }
            );
        }

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
    private void checkUnionTypeExtension(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, String name, List<UnionTypeExtensionDefinition> extensions) {
        checkTypeExtensionHasCorrespondingType(errors, typeRegistry, name, extensions, UnionTypeDefinition.class);

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

        Map<String, FieldDefinition> referenceFields = FpKit.getByName(referenceFieldDefinitions, FieldDefinition::getName, mergeFirst());

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
        Map<String, InputValueDefinition> referenceFields = FpKit.getByName(referenceInputValues, InputValueDefinition::getName, mergeFirst());

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

}
