package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.rules.SchemaValidationRule;
import graphql.schema.validation.rules.NoUnbrokenInputCyclesRule;
import graphql.schema.validation.rules.ObjectsImplementInterfaces;
import graphql.schema.validation.rules.DirectiveRule;
import graphql.schema.validation.rules.FieldDefinitionRule;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;


// todo 添加规则
@Internal
public class SchemaValidator {

    private List<SchemaValidationRule> rules = new ArrayList<>();

    public SchemaValidator() {
        rules.add(new NoUnbrokenInputCyclesRule());
        rules.add(new ObjectsImplementInterfaces());
//        rules.add(new DirectiveRule());
//        rules.add(new FieldDefinitionRule());
    }

    SchemaValidator(List<SchemaValidationRule> rules) {
        this.rules = rules;
    }

    //获取所有的规则
    public List<SchemaValidationRule> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();

        for (SchemaValidationRule rule : rules) {
            rule.check(schema,validationErrorCollector);
        }

        return validationErrorCollector.getErrors();
    }

}