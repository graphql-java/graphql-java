package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.*;
import graphql.schema.validation.rules.*;

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
        rules.add(new ObjectsImplementInterfaces());
        rules.add(new SchemaDirectiveRule());
        rules.add(new FieldDefinitionRule());
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

        checkDirectives(schema.getDirectives(),validationErrorCollector);
        checkFieldDefinitions(schema.getQueryType(),validationErrorCollector);

        traverse(schema.getQueryType(), validationErrorCollector);
        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(),  validationErrorCollector);
        }
        if (schema.isSupportingSubscriptions()) {
            traverse(schema.getSubscriptionType(),validationErrorCollector);
        }
        return validationErrorCollector.getErrors();
    }

    private void checkFieldDefinitions(GraphQLObjectType queryType, SchemaValidationErrorCollector validationErrorCollector) {
        GraphQLObjectType thisQueryType=queryType;
        for (SchemaValidationRule rule : rules) {
            rule.check(thisQueryType,validationErrorCollector);
        }
    }

    private void checkDirectives(List<GraphQLDirective> schemaDirectives, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLDirective> thisDirectives=schemaDirectives;
        for (SchemaValidationRule rule : rules) {
            rule.check(thisDirectives,validationErrorCollector);
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

    private void traverse(GraphQLOutputType root, SchemaValidationErrorCollector validationErrorCollector) {
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
                traverse(fieldDefinition.getType(), validationErrorCollector);
            }
        }
    }
}