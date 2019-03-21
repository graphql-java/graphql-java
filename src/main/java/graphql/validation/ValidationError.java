package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.i18n.I18N;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ValidationError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorType validationErrorType;
    private final List<String> queryPath;

    @Deprecated
    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, (SourceLocation) null, null);
    }

    @Deprecated
    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this(validationErrorType, nullOrList(sourceLocation), description, null, defaultedI18n());
    }

    @Deprecated
    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description, List<String> queryPath) {
        this(validationErrorType, nullOrList(sourceLocation), description, queryPath, defaultedI18n());
    }

    @Deprecated
    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this(validationErrorType, sourceLocations, description, null, defaultedI18n());
    }

    private static I18N defaultedI18n() {
        return I18N.i18n(I18N.BundleType.Validation, Locale.getDefault());
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<String> queryPath, I18N i18n) {
        this.validationErrorType = validationErrorType;
        if (sourceLocations != null) {
            this.locations.addAll(sourceLocations);
        }
        this.description = description;
        this.message = mkMessage(i18n, validationErrorType, description, queryPath);
        this.queryPath = queryPath;
    }

    private static List<SourceLocation> nullOrList(SourceLocation sourceLocation) {
        return sourceLocation == null ? null : Collections.singletonList(sourceLocation);
    }

    private String mkMessage(I18N i18n, ValidationErrorType validationErrorType, String description, List<String> queryPath) {
        if (queryPath == null) {
            return i18n.msg("ValidationError.coveringMsgNoPath", validationErrorType, description);
        } else {
            return i18n.msg("ValidationError.coveringMsg", validationErrorType, description, String.join("/", queryPath));
        }
    }

    public ValidationErrorType getValidationErrorType() {
        return validationErrorType;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public List<String> getQueryPath() {
        return queryPath;
    }


    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", queryPath=" + queryPath +
                ", message=" + message +
                ", locations=" + locations +
                ", description='" + description + '\'' +
                '}';
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }

}
