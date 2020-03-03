package graphql;

import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

import java.util.List;

/**
 * This class allows you to parse and validate a graphql query without executing it.  It will tell you
 * if its syntactically valid and also semantically valid according to the graphql specification
 * and the provided schema.
 */
@PublicApi
public class ParseAndValidate {

    /**
     * This can be called to parse and validate a graphql query against a schema, which is useful if you want to know if it would be acceptable
     * for execution.
     *
     * @param graphQLSchema  the schema to validate against
     * @param executionInput the execution input containing the query
     * @return a result object that indicates how this operation went
     */
    public static ParseAndValidateResult parseAndValidate(GraphQLSchema graphQLSchema, ExecutionInput executionInput) {
        ParseAndValidateResult result = parse(executionInput);
        if (!result.isFailure()) {
            List<ValidationError> errors = validate(graphQLSchema, result.getDocument());
            return result.of(errors);
        }
        return result;
    }

    /**
     * This can be called to parse a graphql query.
     *
     * @param executionInput the input containing the query
     * @return a result object that indicates how this operation went
     */
    public static ParseAndValidateResult parse(ExecutionInput executionInput) {
        try {
            Parser parser = new Parser();
            Document document = parser.parseDocument(executionInput.getQuery());
            return ParseAndValidateResult.of(document, executionInput.getVariables());
        } catch (InvalidSyntaxException e) {
            return ParseAndValidateResult.ofError(e, executionInput.getVariables());
        }
    }

    /**
     * This can be called to parse a graphql query.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(GraphQLSchema graphQLSchema, Document parsedDocument) {
        Validator validator = new Validator();
        return validator.validateDocument(graphQLSchema, parsedDocument);
    }
}
