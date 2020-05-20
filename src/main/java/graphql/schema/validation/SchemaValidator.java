package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.rule.SchemaValidationRule;
import graphql.schema.validation.rule.NoUnbrokenInputCycles;
import graphql.schema.validation.rule.ObjectsImplementInterfaces;
import graphql.schema.validation.rule.TypeRule;
import graphql.schema.validation.rule.DirectiveRule;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Internal
public class SchemaValidator {


    private List<SchemaValidationRule> rules = new ArrayList<>();

    public SchemaValidator() {
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new ObjectsImplementInterfaces());
        rules.add(new TypeRule());
        rules.add(new DirectiveRule());
    }

    SchemaValidator(List<SchemaValidationRule> rules) {
        this.rules = rules;
    }

    public List<SchemaValidationRule> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();

        for (SchemaValidationRule rule : rules) {
            rule.apply(schema, validationErrorCollector);
        }

        return validationErrorCollector.getErrors();
    }


}
