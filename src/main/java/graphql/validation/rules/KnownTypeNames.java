package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.schema.SchemaUtil;
import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;

public class KnownTypeNames extends AbstractRule {

    public KnownTypeNames(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkTypeName(TypeName typeName) {
        if (SchemaUtil.findType(getValidationContext().getSchema(), typeName.getName()) == null) {
            addError(new ValidationError("Invalid type name " + typeName.getName()));
        }
    }
}
