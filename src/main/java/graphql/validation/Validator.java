package graphql.validation;


import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Internal
public class Validator {

    static int MAX_VALIDATION_ERRORS = 100;

    /**
     * `graphql-java` will stop validation after a maximum number of validation messages has been reached.  Attackers
     * can send pathologically invalid queries to induce a Denial of Service attack and fill memory with 10000s of errors
     * and burn CPU in process.
     * <p>
     * By default, this is set to 100 errors.  You can set a new JVM wide value as the maximum allowed validation errors.
     *
     * @param maxValidationErrors the maximum validation errors allow JVM wide
     */
    public static void setMaxValidationErrors(int maxValidationErrors) {
        MAX_VALIDATION_ERRORS = maxValidationErrors;
    }

    public static int getMaxValidationErrors() {
        return MAX_VALIDATION_ERRORS;
    }

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document, Locale locale) {
        return validateDocument(schema, document, rule -> true, locale);
    }

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document, Predicate<OperationValidationRule> rulePredicate, Locale locale) {
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, locale);
        ValidationContext validationContext = new ValidationContext(schema, document, i18n);

        ValidationErrorCollector validationErrorCollector = new ValidationErrorCollector(MAX_VALIDATION_ERRORS);
        OperationValidator operationValidator = new OperationValidator(validationContext, validationErrorCollector, rulePredicate);
        LanguageTraversal languageTraversal = new LanguageTraversal();
        try {
            languageTraversal.traverse(document, operationValidator);
        } catch (ValidationErrorCollector.MaxValidationErrorsReached ignored) {
            // if we have generated enough errors, then we can shortcut out
        }

        return validationErrorCollector.getErrors();
    }
}
