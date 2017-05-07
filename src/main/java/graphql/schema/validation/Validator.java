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

public class Validator {

    private final Set<GraphQLOutputType> processed = new HashSet<>();

    public Set<ValidationError> validateSchema(GraphQLSchema schema) {
        ValidationErrorCollector validationErrorCollector = new ValidationErrorCollector();
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new ObjectsImplementInterfaces());

        List<GraphQLType> types = schema.getAllTypesAsList();
        types.forEach(type -> {
            for (ValidationRule rule : rules) {
                rule.check(type, validationErrorCollector);
            }
        });

        traverse(schema.getQueryType(), rules, validationErrorCollector);
        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(), rules, validationErrorCollector);
        }
        return validationErrorCollector.getErrors();
    }

    private void traverse(GraphQLOutputType root, List<ValidationRule> rules, ValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
            for (GraphQLFieldDefinition fieldDefinition : ((GraphQLFieldsContainer) root).getFieldDefinitions()) {
                for (ValidationRule rule : rules) {
                    rule.check(fieldDefinition, validationErrorCollector);
                }
                traverse(fieldDefinition.getType(), rules, validationErrorCollector);
            }
        }
    }
}
