package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.validation.*;

/**
 * <p>KnownTypeNames class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class KnownTypeNames extends AbstractRule {


    /**
     * <p>Constructor for KnownTypeNames.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public KnownTypeNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkTypeName(TypeName typeName) {
        if ((getValidationContext().getSchema().getType(typeName.getName())) == null) {
            String message = String.format("Unknown type %s", typeName.getName());
            addError(new ValidationError(ValidationErrorType.UnknownType, typeName.getSourceLocation(), message));
        }
    }
}
