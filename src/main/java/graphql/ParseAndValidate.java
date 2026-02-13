package graphql;

import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;
import graphql.schema.GraphQLSchema;
import graphql.validation.OperationValidationRule;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;
import static java.util.Optional.ofNullable;

/**
 * This class allows you to parse and validate a graphql query without executing it.  It will tell you
 * if it's syntactically valid and also semantically valid according to the graphql specification
 * and the provided schema.
 */
@PublicApi
@NullMarked
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
    public static ParseAndValidateResult parseAndValidate(@NonNull GraphQLSchema graphQLSchema, @NonNull ExecutionInput executionInput) {
        ParseAndValidateResult result = parse(executionInput);
        if (!result.isFailure()) {
            List<ValidationError> errors = validate(graphQLSchema, assertNotNull(result.getDocument(), "Parse result document cannot be null when parse succeeded"), executionInput.getLocale());
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
    public static ParseAndValidateResult parse(@NonNull ExecutionInput executionInput) {
        try {
            //
            // we allow the caller to specify new parser options by context
            ParserOptions parserOptions = executionInput.getGraphQLContext().get(ParserOptions.class);
            // we use the query parser options by default if they are not specified
            parserOptions = ofNullable(parserOptions).orElse(ParserOptions.getDefaultOperationParserOptions());
            Parser parser = new Parser();
            Locale locale = executionInput.getLocale() == null ? Locale.getDefault() : executionInput.getLocale();
            ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment()
                    .document(executionInput.getQuery()).parserOptions(parserOptions)
                    .locale(locale)
                    .build();
            Document document = parser.parseDocument(parserEnvironment);
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
     * @param locale         the current locale
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(@NonNull GraphQLSchema graphQLSchema, @NonNull Document parsedDocument, @NonNull Locale locale) {
        return validate(graphQLSchema, parsedDocument, rule -> true, locale);
    }

    /**
     * This can be called to validate a parsed graphql query, with the JVM default locale.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(@NonNull GraphQLSchema graphQLSchema, @NonNull Document parsedDocument) {
        return validate(graphQLSchema, parsedDocument, rule -> true, Locale.getDefault());
    }

    /**
     * This can be called to validate a parsed graphql query.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     * @param rulePredicate  this predicate is used to decide what validation rules will be applied
     * @param locale         the current locale
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(@NonNull GraphQLSchema graphQLSchema, @NonNull Document parsedDocument, @NonNull Predicate<OperationValidationRule> rulePredicate, @NonNull Locale locale) {
        Validator validator = new Validator();
        return validator.validateDocument(graphQLSchema, parsedDocument, rulePredicate, locale);
    }

    /**
     * This can be called to validate a parsed graphql query, with the JVM default locale.
     *
     * @param graphQLSchema  the graphql schema to validate against
     * @param parsedDocument the previously parsed document
     * @param rulePredicate  this predicate is used to decide what validation rules will be applied
     *
     * @return a result object that indicates how this operation went
     */
    public static List<ValidationError> validate(@NonNull GraphQLSchema graphQLSchema, @NonNull Document parsedDocument, @NonNull Predicate<OperationValidationRule> rulePredicate) {
        Validator validator = new Validator();
        return validator.validateDocument(graphQLSchema, parsedDocument, rulePredicate, Locale.getDefault());
    }
}
