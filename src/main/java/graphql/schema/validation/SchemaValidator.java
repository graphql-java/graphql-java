package graphql.schema.validation;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SchemaValidator {

    private final Set<GraphQLOutputType> processed = new HashSet<>();

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();
        List<SchemaValidationRule> rules = new ArrayList<>();
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new ObjectsImplementInterfaces());

        List<GraphQLType> types = schema.getAllTypesAsList();
        types.forEach(type -> {
            for (SchemaValidationRule rule : rules) {
                rule.check(type, validationErrorCollector);
            }
        });

        traverse(schema.getQueryType(), rules, validationErrorCollector);
        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(), rules, validationErrorCollector);
        }
        if (schema.isSupportingSubscriptions()) {
            traverse(schema.getSubscriptionType(), rules, validationErrorCollector);
        }
        return validationErrorCollector.getErrors();
    }

    private void traverse(GraphQLOutputType root, List<SchemaValidationRule> rules, SchemaValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
            for (GraphQLFieldDefinition fieldDefinition : ((GraphQLFieldsContainer) root).getFieldDefinitions()) {
                for (SchemaValidationRule rule : rules) {
                    rule.check(fieldDefinition, validationErrorCollector);
                }
                traverse(fieldDefinition.getType(), rules, validationErrorCollector);
            }
        }
    }
}
