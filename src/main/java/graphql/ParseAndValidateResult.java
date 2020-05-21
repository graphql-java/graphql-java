package graphql;

import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    private ParseAndValidateResult(Builder builder) {
        this.document = builder.document;
        this.variables = builder.variables == null ? Collections.emptyMap() : builder.variables;
        this.syntaxException = builder.syntaxException;
        this.validationErrors = builder.validationErrors == null ? Collections.emptyList() : builder.validationErrors;
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

    public ParseAndValidateResult transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder()
                .document(document).variables(variables).syntaxException(syntaxException).validationErrors(validationErrors);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newResult() {
        return new Builder();
    }

    public static class Builder {
        private Document document;
        private Map<String, Object> variables = Collections.emptyMap();
        private InvalidSyntaxException syntaxException;
        private List<ValidationError> validationErrors = Collections.emptyList();

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder validationErrors(List<ValidationError> validationErrors) {
            this.validationErrors = validationErrors;
            return this;
        }

        public Builder syntaxException(InvalidSyntaxException syntaxException) {
            this.syntaxException = syntaxException;
            return this;
        }

        public ParseAndValidateResult build() {
            return new ParseAndValidateResult(this);
        }
    }
}
