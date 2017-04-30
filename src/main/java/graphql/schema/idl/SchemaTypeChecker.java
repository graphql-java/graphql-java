package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.MissingScalarImplementationError;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.MissingTypeResolverError;
import graphql.schema.idl.errors.OperationTypesMustBeObjects;
import graphql.schema.idl.errors.QueryOperationMissingError;
import graphql.schema.idl.errors.SchemaMissingError;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This helps pre check the state of the type system to ensure it can be made into an executable schema.
 *
 * It looks for missing types and ensure certain invariants are true before a schema can be made.
 */
public class SchemaTypeChecker {

    public List<GraphQLError> checkTypeRegistry(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {
        List<GraphQLError> errors = new ArrayList<>();
        checkForMissingTypes(errors, typeRegistry);
        checkSchemaInvariants(errors, typeRegistry);

        checkScalarImplementationsArePresent(errors, typeRegistry, wiring);
        checkTypeResolversArePresent(errors, typeRegistry, wiring);

        return errors;

    }

    private void checkSchemaInvariants(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        // schema
        if (!typeRegistry.schemaDefinition().isPresent()) {
            errors.add(new SchemaMissingError());
        } else {
            SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
            List<OperationTypeDefinition> operationTypeDefinitions = schemaDefinition.getOperationTypeDefinitions();

            operationTypeDefinitions
                    .forEach(checkOperationTypesExist(typeRegistry, errors));

            operationTypeDefinitions
                    .forEach(checkOperationTypesAreObjects(typeRegistry, errors));

            // ensure we have a "query" one
            Optional<OperationTypeDefinition> query = operationTypeDefinitions.stream().filter(op -> "query".equals(op.getName())).findFirst();
            if (!query.isPresent()) {
                errors.add(new QueryOperationMissingError());
            }

        }
    }

    private void checkForMissingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        // type extensions
        Collection<TypeExtensionDefinition> typeExtensions = typeRegistry.typeExtensions().values();
        typeExtensions.forEach(typeExtension -> {

            List<Type> implementsTypes = typeExtension.getImplements();
            implementsTypes.forEach(checkTypeExists("type extension", typeRegistry, errors, typeExtension));

            checkFieldTypesPresent(typeRegistry, errors, typeExtension, typeExtension.getFieldDefinitions());

        });


        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> {

            List<Type> implementsTypes = objectType.getImplements();
            implementsTypes.forEach(checkTypeExists("object", typeRegistry, errors, objectType));

            checkFieldTypesPresent(typeRegistry, errors, objectType, objectType.getFieldDefinitions());

        });

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> {
            List<FieldDefinition> fields = interfaceType.getFieldDefinitions();

            checkFieldTypesPresent(typeRegistry, errors, interfaceType, fields);

        });

        // union types
        List<UnionTypeDefinition> unionTypes = filterTo(typesMap, UnionTypeDefinition.class);
        unionTypes.forEach(unionType -> {
            List<Type> memberTypes = unionType.getMemberTypes();
            memberTypes.forEach(checkTypeExists("union member", typeRegistry, errors, unionType));

        });


        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> {
            List<InputValueDefinition> inputValueDefinitions = inputType.getInputValueDefinitions();
            List<Type> inputValueTypes = inputValueDefinitions.stream()
                    .map(InputValueDefinition::getType)
                    .collect(Collectors.toList());

            inputValueTypes.forEach(checkTypeExists("input value", typeRegistry, errors, inputType));

        });
    }

    private void checkScalarImplementationsArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
        typeRegistry.scalars().keySet().forEach(scalarName -> {
            if (!wiring.getScalars().containsKey(scalarName)) {
                errors.add(new MissingScalarImplementationError(scalarName));
            }
        });
    }

    private void checkTypeResolversArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {

        Consumer<TypeDefinition> checkForResolver = typeDef -> {
            if (!wiring.getTypeResolvers().containsKey(typeDef.getName())) {
                errors.add(new MissingTypeResolverError(typeDef));
            }
        };

        typeRegistry.types().values().stream()
                .filter(typeDef -> typeDef instanceof InterfaceTypeDefinition || typeDef instanceof UnionTypeDefinition)
                .forEach(checkForResolver);

    }


    private void checkFieldTypesPresent(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fields) {
        List<Type> fieldTypes = fields.stream().map(FieldDefinition::getType).collect(Collectors.toList());
        fieldTypes.forEach(checkTypeExists("field", typeRegistry, errors, typeDefinition));

        List<Type> fieldInputValues = fields.stream()
                .map(f -> f.getInputValueDefinitions()
                        .stream()
                        .map(InputValueDefinition::getType)
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        fieldInputValues.forEach(checkTypeExists("field input", typeRegistry, errors, typeDefinition));
    }

    private Consumer<Type> checkTypeExists(String typeOfType, TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeName unwrapped = TypeInfo.typeInfo(t).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError(typeOfType, typeDefinition, unwrapped));
            }
        };
    }

    private Consumer<OperationTypeDefinition> checkOperationTypesExist(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            TypeName unwrapped = TypeInfo.typeInfo(op.getType()).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError("operation", op, op.getName(), unwrapped));
            }
        };
    }

    private Consumer<OperationTypeDefinition> checkOperationTypesAreObjects(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            // make sure it is defined as a ObjectTypeDef
            Type queryType = op.getType();
            Optional<TypeDefinition> type = typeRegistry.getType(queryType);
            type.ifPresent(typeDef -> {
                if (!(typeDef instanceof ObjectTypeDefinition)) {
                    errors.add(new OperationTypesMustBeObjects(op));
                }
            });
        };
    }

    private <T extends TypeDefinition> List<T> filterTo(Map<String, TypeDefinition> types, Class<? extends T> clazz) {
        return types.values().stream()
                .filter(t -> clazz.equals(t.getClass()))
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

}
