package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTraverser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
public class SchemaValidator {


    private final List<GraphQLTypeVisitor> rules = new ArrayList<>();

    public SchemaValidator() {
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new TypesImplementInterfaces());
        rules.add(new TypeAndFieldRule());
        rules.add(new DefaultValuesAreValid());
        rules.add(new AppliedDirectivesAreValid());
        rules.add(new AppliedDirectiveArgumentsAreValid());
        rules.add(new InputAndOutputTypesUsedAppropriately());
        rules.add(new OneOfInputObjectRules());
        rules.add(new DeprecatedInputObjectAndArgumentsAreValid());
    }

    public List<GraphQLTypeVisitor> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema, GraphQLCodeRegistry codeRegistry) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();
        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(GraphQLSchema.class, schema);
        rootVars.put(SchemaValidationErrorCollector.class, validationErrorCollector);
        rootVars.put(GraphQLCodeRegistry.class, codeRegistry);
        List<GraphQLTypeVisitor> appliedRules = rules;
        if (true) {
            appliedRules = new ArrayList<>(rules);
            appliedRules.add(codeRegistry.validationRule());
        }
        new SchemaTraverser().depthFirstFullSchema(appliedRules, schema, rootVars);
        return validationErrorCollector.getErrors();
    }

}
