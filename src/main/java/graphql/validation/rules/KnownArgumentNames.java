package graphql.validation.rules;

import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.*;


public class KnownArgumentNames extends AbstractRule {

    public KnownArgumentNames(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }


    @Override
    public void checkArgument(Argument argument) {
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) return;
        GraphQLArgument fieldArgument = fieldDef.getArgument(argument.getName());
        if(fieldArgument == null){
            addError(new ValidationError(ValidationErrorType.UnknownArgument));
        }
    }
}
