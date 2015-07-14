package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SchemaUtil;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationError;

public class ScalarLeafs extends AbstractRule {


    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getType();
        if (SchemaUtil.isLeafType(type)) {
            if (field.getSelectionSet() != null) {
                getValidationContext().addError(new ValidationError("No subselection allowed on " + field.getName()));
            }
        } else {
            if (field.getSelectionSet() != null) {
                getValidationContext().addError(new ValidationError("Subseletion required on " + field.getName()));
            }
        }
    }
}
