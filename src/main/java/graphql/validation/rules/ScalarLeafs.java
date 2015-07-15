package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SchemaUtil;
import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;

public class ScalarLeafs extends AbstractRule {


    public ScalarLeafs(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getType();
        if (SchemaUtil.isLeafType(type)) {
            if (field.getSelectionSet() != null) {
                addError(new ValidationError("No subselection allowed on " + field.getName()));
            }
        } else {
            if (field.getSelectionSet() != null) {
                addError(new ValidationError("Subseletion required on " + field.getName()));
            }
        }
    }
}
