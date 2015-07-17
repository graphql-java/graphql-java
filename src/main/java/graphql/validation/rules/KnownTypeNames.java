package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

public class KnownTypeNames extends AbstractRule {

    private SchemaUtil schemaUtil = new SchemaUtil();

    public KnownTypeNames(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkTypeName(TypeName typeName) {
        if (schemaUtil.findType(getValidationContext().getSchema(), typeName.getName()) == null) {
            addError(new ValidationError(ValidationErrorType.UnknownType));
        }
    }
}
