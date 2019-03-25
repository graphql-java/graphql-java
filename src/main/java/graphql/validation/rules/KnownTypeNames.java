package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import static graphql.validation.ValidationErrorType.UnknownType;

public class KnownTypeNames extends AbstractRule {


    public KnownTypeNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkTypeName(TypeName typeName) {
        if ((getValidationContext().getSchema().getType(typeName.getName())) == null) {
            String message = i18n("KnownTypeNames.unknownType", UnknownType, typeName.getName());
            addError(UnknownType, typeName.getSourceLocation(), message);
        }
    }
}
