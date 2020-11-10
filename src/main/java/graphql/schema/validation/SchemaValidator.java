package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Internal
public class SchemaValidator {

    private final Set<GraphQLOutputType> processed = new LinkedHashSet<>();

    private List<SchemaValidationRule> rules = new ArrayList<>();

    public SchemaValidator() {
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new TypesImplementInterfaces());
        rules.add(new TypeAndFieldRule());
    }

    SchemaValidator(List<SchemaValidationRule> rules) {
        this.rules = rules;
    }

    public List<SchemaValidationRule> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();

        checkTypes(schema, validationErrorCollector);
        checkSchema(schema, validationErrorCollector);

        traverse(schema.getQueryType(), rules, validationErrorCollector);
        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(), rules, validationErrorCollector);
        }
        if (schema.isSupportingSubscriptions()) {
            traverse(schema.getSubscriptionType(), rules, validationErrorCollector);
        }
        return validationErrorCollector.getErrors();
    }

    private void checkSchema(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        for (SchemaValidationRule rule : rules) {
            rule.check(schema, validationErrorCollector);
        }
    }

    private void checkTypes(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLNamedType> types = schema.getAllTypesAsList();
        types.forEach(type -> {
            for (SchemaValidationRule rule : rules) {
                rule.check(type, validationErrorCollector);
            }
        });
    }

    private void traverse(GraphQLOutputType root, List<SchemaValidationRule> rules, SchemaValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
            // this deliberately has open field visibility here since its validating the schema
            // when completely open
            for (GraphQLFieldDefinition fieldDefinition : ((GraphQLFieldsContainer) root).getFieldDefinitions()) {
                for (SchemaValidationRule rule : rules) {
                    rule.check(fieldDefinition, validationErrorCollector);
                }
                traverse(fieldDefinition.getType(), rules, validationErrorCollector);
            }
        }
    }
}
