package graphql;

import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A result object used in {@link graphql.ParseAndValidate} helper that indicates the outcomes of a parse
 * and validate operation.
 */
@PublicApi
public class ParseAndValidateResult {

    private final Document document;
    private final Map<String, Object> variables;
    private final InvalidSyntaxException syntaxException;
    private final List<ValidationError> validationErrors;

    private ParseAndValidateResult(Document document, Map<String, Object> variables, InvalidSyntaxException syntaxException, List<ValidationError> validationErrors) {
        this.document = document;
        this.variables = variables == null ? Collections.emptyMap() : variables;
        this.syntaxException = syntaxException;
        this.validationErrors = validationErrors;
    }

    /**
     * @return true if there was a parse exception or the validation failed
     */
    public boolean isFailure() {
        return syntaxException != null || !validationErrors.isEmpty();
    }

    /**
     * @return the parsed document or null if its syntactically invalid.
     */
    public Document getDocument() {
        return document;
    }

    /**
     * @return the document variables or null if its syntactically invalid.
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * @return the parsed document and variables or null if its syntactically invalid.
     */
    public DocumentAndVariables getDocumentAndVariables() {
        if (document != null) {
            return DocumentAndVariables.newDocumentAndVariables().document(document).variables(variables).build();
        }
        return null;
    }

    /**
     * @return the syntax exception or null if its syntactically valid.
     */
    public InvalidSyntaxException getSyntaxException() {
        return syntaxException;
    }

    /**
     * @return a list of validation errors, which might be empty if its syntactically invalid.
     */
    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    /**
     * A list of all the errors (parse and validate) that have occurred
     *
     * @return the errors that have occurred or empty list if there are none
     */
    public List<GraphQLError> getErrors() {
        List<GraphQLError> errors = new ArrayList<>();
        if (syntaxException != null) {
            errors.add(syntaxException.toInvalidSyntaxError());
        }
        errors.addAll(validationErrors);
        return errors;
    }

    public static ParseAndValidateResult of(Document document, Map<String, Object> variables) {
        return new ParseAndValidateResult(document, variables, null, Collections.emptyList());
    }

    public static ParseAndValidateResult ofError(InvalidSyntaxException e, Map<String, Object> variables) {
        return new ParseAndValidateResult(null, variables, e, Collections.emptyList());
    }

    public ParseAndValidateResult of(List<ValidationError> validationErrors) {
        return new ParseAndValidateResult(this.document, this.variables, this.syntaxException, validationErrors);
    }
}
