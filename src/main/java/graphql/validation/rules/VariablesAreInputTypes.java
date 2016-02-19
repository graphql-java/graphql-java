package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

/**
 * <p>VariablesAreInputTypes class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class VariablesAreInputTypes extends AbstractRule {

    private SchemaUtil schemaUtil = new SchemaUtil();

    /**
     * <p>Constructor for VariablesAreInputTypes.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public VariablesAreInputTypes(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = getValidationUtil().getUnmodifiedType(variableDefinition.getType());

        GraphQLType type = getValidationContext().getSchema().getType(unmodifiedAstType.getName());
        if (type == null) return;
        if (!schemaUtil.isInputType(type)) {
            String message = "Wrong type for a variable";
            addError(new ValidationError(ValidationErrorType.NonInputTypeOnVariable, variableDefinition.getSourceLocation(), message));
        }
    }
}
