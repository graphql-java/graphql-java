package graphql.validation.rules;

import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.*;


/**
 * <p>KnownArgumentNames class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class KnownArgumentNames extends AbstractRule {

    /**
     * <p>Constructor for KnownArgumentNames.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public KnownArgumentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    /** {@inheritDoc} */
    @Override
    public void checkArgument(Argument argument) {
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) return;
        GraphQLArgument fieldArgument = fieldDef.getArgument(argument.getName());
        if (fieldArgument == null) {
            String message = String.format("Unknown argument %s", argument.getName());
            addError(new ValidationError(ValidationErrorType.UnknownArgument, argument.getSourceLocation(), message));
        }
    }
}
