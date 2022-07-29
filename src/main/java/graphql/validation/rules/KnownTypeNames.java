package graphql.validation.rules;


import graphql.Internal;
import graphql.language.TypeName;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.UnknownType;

@Internal
public class KnownTypeNames extends AbstractRule {


    public KnownTypeNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkTypeName(TypeName typeName) {
        if ((getValidationContext().getSchema().getType(typeName.getName())) == null) {
            String message = i18n(UnknownType, "KnownTypeNames.unknownType", typeName.getName());
            addError(UnknownType, typeName.getSourceLocation(), message);
        }
    }
}
