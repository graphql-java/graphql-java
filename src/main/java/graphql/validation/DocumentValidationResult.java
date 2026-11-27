package graphql.validation;

import graphql.Internal;
import graphql.normalized.NormalizedDocument;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Internal
@NullMarked
public record DocumentValidationResult(List<ValidationError> errors, @Nullable NormalizedDocument normalizedDocument) {
    public DocumentValidationResult {
        errors = List.copyOf(errors);
    }
}
