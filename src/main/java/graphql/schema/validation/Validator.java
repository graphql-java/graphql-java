package graphql.schema.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;

public class Validator {

    private final Set<GraphQLOutputType> processed = new HashSet<>();
    
    public Set<ValidationError> validateSchema(GraphQLSchema schema) {
        ValidationErrorCollector validationErrorCollector = new ValidationErrorCollector();
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new NoUnbrokenInputCycles());
        
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
