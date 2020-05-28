package graphql.schema.idl;

import graphql.Assert;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.Directive;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.OperationRedefinitionError;
import graphql.schema.idl.errors.OperationTypesMustBeObjects;
import graphql.schema.idl.errors.QueryOperationMissingError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Internal
public class SchemaExtensionsChecker {

    static Map<String, OperationTypeDefinition> gatherOperationDefs(TypeDefinitionRegistry typeRegistry) {
        List<GraphQLError> noErrors = new ArrayList<>();
        Map<String, OperationTypeDefinition> operationTypeDefinitionMap = gatherOperationDefs(noErrors, typeRegistry.schemaDefinition().orElse(null), typeRegistry.getSchemaExtensionDefinitions());
        Assert.assertTrue(noErrors.isEmpty(), () -> "If you call this method it MUST have previously been error checked");
        return operationTypeDefinitionMap;
    }

    static Map<String, OperationTypeDefinition> gatherOperationDefs(List<GraphQLError> errors, SchemaDefinition schema, List<SchemaExtensionDefinition> schemaExtensionDefinitions) {
        Map<String, OperationTypeDefinition> operationDefs = new LinkedHashMap<>();
        if (schema != null) {
            defineOperationDefs(errors, schema.getOperationTypeDefinitions(), operationDefs);
        }
        for (SchemaExtensionDefinition schemaExtensionDefinition : schemaExtensionDefinitions) {
            defineOperationDefs(errors, schemaExtensionDefinition.getOperationTypeDefinitions(), operationDefs);
        }
        return operationDefs;
    }

    static void defineOperationDefs(List<GraphQLError> errors, Collection<OperationTypeDefinition> newOperationDefs, Map<String, OperationTypeDefinition> existingOperationDefs) {
        for (OperationTypeDefinition operationTypeDefinition : newOperationDefs) {
            OperationTypeDefinition oldEntry = existingOperationDefs.get(operationTypeDefinition.getName());
            if (oldEntry != null) {
                errors.add(new OperationRedefinitionError(oldEntry, operationTypeDefinition));
            } else {
                existingOperationDefs.put(operationTypeDefinition.getName(), operationTypeDefinition);
            }
        }
    }

    static List<OperationTypeDefinition> checkSchemaInvariants(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        /*
            https://github.com/facebook/graphql/pull/90/files#diff-fe406b08746616e2f5f00909488cce66R1000

            GraphQL type system definitions can omit the schema definition when the query
            and mutation root types are named `Query` and `Mutation`, respectively.
         */
        // schema
        SchemaDefinition schemaDef = typeRegistry.schemaDefinition().orElse(null);
        Map<String, OperationTypeDefinition> operationTypeMap = SchemaExtensionsChecker.gatherOperationDefs(errors, schemaDef, typeRegistry.getSchemaExtensionDefinitions());
        List<OperationTypeDefinition> operationTypeDefinitions = new ArrayList<>(operationTypeMap.values());

        operationTypeDefinitions
                .forEach(checkOperationTypesExist(typeRegistry, errors));

        operationTypeDefinitions
                .forEach(checkOperationTypesAreObjects(typeRegistry, errors));

        // ensure we have a "query" one
        Optional<OperationTypeDefinition> query = operationTypeDefinitions.stream().filter(op -> "query".equals(op.getName())).findFirst();
        if (!query.isPresent()) {
            // its ok if they have a type named Query
            Optional<TypeDefinition> queryType = typeRegistry.getType("Query");
            if (!queryType.isPresent()) {
                errors.add(new QueryOperationMissingError());
            }
        }
        return operationTypeDefinitions;
    }

    static List<Directive> gatherSchemaDirectives(TypeDefinitionRegistry typeRegistry) {
        List<GraphQLError> noErrors = new ArrayList<>();
        List<Directive> directiveList = gatherSchemaDirectives(typeRegistry, noErrors);
        Assert.assertTrue(noErrors.isEmpty(), () -> "If you call this method it MUST have previously been error checked");
        return directiveList;
    }

    static List<Directive> gatherSchemaDirectives(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        List<Directive> directiveList = new ArrayList<>();
        SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().orElse(null);
        if (schemaDefinition != null) {
            directiveList.addAll(schemaDefinition.getDirectives());
        }
        for (SchemaExtensionDefinition schemaExtensionDefinition : typeRegistry.getSchemaExtensionDefinitions()) {
            directiveList.addAll(schemaExtensionDefinition.getDirectives());
        }
        return directiveList;
    }

    private static Consumer<OperationTypeDefinition> checkOperationTypesExist(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            TypeName unwrapped = TypeInfo.typeInfo(op.getTypeName()).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError("operation", op, op.getName(), unwrapped));
            }
        };
    }

    private static Consumer<OperationTypeDefinition> checkOperationTypesAreObjects(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            // make sure it is defined as a ObjectTypeDef
            Type queryType = op.getTypeName();
            Optional<TypeDefinition> type = typeRegistry.getType(queryType);
            type.ifPresent(typeDef -> {
                if (!(typeDef instanceof ObjectTypeDefinition)) {
                    errors.add(new OperationTypesMustBeObjects(op));
                }
            });
        };
    }

}
