package graphql;

import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.parser.ParserOptions;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

import java.util.List;
import java.util.function.Predicate;

/**
 * This class allows you to parse and validate a graphql query without executing it.  It will tell you
 * if its syntactically valid and also semantically valid according to the graphql specification
 * and the provided schema.
 */
@PublicApi
public class ParseAndValidate {

    /**
     * This {@link GraphQLContext} hint can be used to supply a Predicate to the Validator so that certain rules can be skipped.
     *
     * This is an internal capability that you should use at your own risk.  While we intend for this to be present for some time, the validation
     * rule class names may change, as may this mechanism.
     */
    @Internal
    public static final String INTERNAL_VALIDATION_PREDICATE_HINT = "graphql.ParseAndValidate.Predicate";

    /**
     * This can be called to parse and validate a graphql query against a schema, which is useful if you want to know if it would be acceptable
     * for execution.
     *
     * @param graphQLSchema  the schema to validate against
     * @param executionInput the execution input containing the query
     *
     * @return a result object that indicates how this operation went
     */
    public static ParseAndValidateResult parseAndValidate(GraphQLSchema graphQLSchema, ExecutionInput executionInput) {
        ParseAndValidateResult result = parse(executionInput);
        if (!result.isFailure()) {
            List<ValidationError> errors = validate(graphQLSchema, result.getDocument());
            return result.transform(builder -> builder.validationErrors(errors));
        }
        return result;
    }

    /**
     * This can be called to parse (but not validate) a graphql query.
     *
     * @param executionInput the input containing the query
     *
     * @return a result object that indicates how this operation went
     */
    public static ParseAndValidateResult parse(ExecutionInput executionInput) {
        try {
            //
            // we allow the caller to specify new parser options by context
            ParserOptions parserOptions = executionInput.getGraphQLContext().get(ParserOptions.class);
            Parser parser = new Parser();
            Document document = parser.parseDocument(executionInput.getQuery(), parserOptions);
            return ParseAndValidateResult.newResult().document(document).variables(executionInput.getVariables()).build();
        } catch (InvalidSyntaxException e) {
            return ParseAndValidateResult.newResult().syntaxException(e).variables(executionInput.getVariables()).build();
        }
    }

    /**
     * This can be called to validate a parsed graphql query.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(GraphQLSchema graphQLSchema, Document parsedDocument) {
        return validate(graphQLSchema, parsedDocument, ruleClass -> true);
    }

    /**
     * This can be called to validate a parsed graphql query.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     * @param rulePredicate  this predicate is used to decide what validation rules will be applied
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(GraphQLSchema graphQLSchema, Document parsedDocument, Predicate<Class<?>> rulePredicate) {
        Validator validator = new Validator();
        return validator.validateDocument(graphQLSchema, parsedDocument, rulePredicate);
    }
}
