package graphql.execution.prevalidated;

import graphql.ExecutionInput;
import graphql.Internal;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.function.Supplier;

@Internal
public class NoOpPreValidationProvider implements PreValidationProvider {

    public final static PreValidationProvider INSTANCE = new NoOpPreValidationProvider();

    @Override
    public List<ValidationError> get(ExecutionInput query, Document queryDocument, GraphQLSchema graphQLSchema, Supplier<List<ValidationError>> validationFunction) {
        return validationFunction.get();
    }
}
