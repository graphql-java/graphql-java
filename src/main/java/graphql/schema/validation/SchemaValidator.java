package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTraverser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.validation.SchemaValidationErrorType.UnresolvedTypeReference;
import static java.lang.String.format;

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
        rules.add(new UniqueNamesAreValid());
        rules.add(new DirectiveApplicationIsValid());
        rules.add(new DirectiveDefinitionsAreValid());
    }

    public List<GraphQLTypeVisitor> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();
        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(GraphQLSchema.class, schema);
        rootVars.put(SchemaValidationErrorCollector.class, validationErrorCollector);
        new SchemaTraverser().depthFirstFullSchema(rules, schema, rootVars);

        // Post-traversal checks
        checkForUnresolvedTypeReferences(schema, validationErrorCollector);
        DirectiveApplicationIsValid.checkSchemaDirectives(schema, validationErrorCollector);

        return validationErrorCollector.getErrors();
    }

    private void checkForUnresolvedTypeReferences(GraphQLSchema schema, SchemaValidationErrorCollector errorCollector) {
        for (GraphQLNamedType type : schema.getAllTypesAsList()) {
            if (type instanceof GraphQLTypeReference) {
                errorCollector.addError(new SchemaValidationError(UnresolvedTypeReference,
                        format("Unresolved type reference '%s'", type.getName())));
            }
        }
    }
}
