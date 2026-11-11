package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;

/**
 * Schema validation rule ensuring applied directives are defined in the schema and that non repeatable
 * directives are applied at most once.
 */
@Internal
@NullMarked
public class AppliedDirectivesAreValid implements SchemaValidationRule {

    private final GraphQLSchema schema;
    private final SchemaValidationErrorCollector errors;

    public AppliedDirectivesAreValid(GraphQLSchema schema, SchemaValidationErrorCollector errors) {
        this.schema = schema;
        this.errors = errors;
    }

    @Override
    public void checkDirectiveContainer(GraphQLDirectiveContainer container) {
        Map<String, List<GraphQLDirective>> directivesByName = container.getAllDirectivesByName();
        if (directivesByName.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<GraphQLDirective>> entry : directivesByName.entrySet()) {
            GraphQLDirective directiveDef = schema.getDirective(entry.getKey());
            if (directiveDef == null) {
                addError(SchemaValidationErrorType.InvalidAppliedDirective, format("A definition for directive '%s' could not be found", entry.getKey()));
            } else if (directiveDef.isNonRepeatable() && entry.getValue().size() > 1) {
                addError(SchemaValidationErrorType.InvalidAppliedDirective,
                        format("The directive '%s' on the '%s' called '%s' is a non repeatable directive but has been applied %d times",
                                directiveDef.getName(), container.getClass().getSimpleName(), container.getName(), entry.getValue().size()));
            }
        }
    }

    private void addError(SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }
}
