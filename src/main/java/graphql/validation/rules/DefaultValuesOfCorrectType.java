package graphql.validation.rules;

import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.validation.*;


public class DefaultValuesOfCorrectType extends AbstractRule {


    public DefaultValuesOfCorrectType(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }


//        var name = varDefAST.variable.name.value;
//        var defaultValue = varDefAST.defaultValue;
//        var type = context.getInputType();
//        if (type instanceof GraphQLNonNull && defaultValue) {
//            return new GraphQLError(
//                    defaultForNonNullArgMessage(name, type, type.ofType),
//                    [defaultValue]
//            );
//        }
//        if (type && defaultValue && !isValidLiteralValue(defaultValue, type)) {
//            return new GraphQLError(
//                    badValueForDefaultArgMessage(name, type, print(defaultValue)),
//                    [defaultValue]
//            );
//        }
//    }


    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = getValidationContext().getInputType();
        if(inputType == null) return;
        if(inputType instanceof GraphQLNonNull && variableDefinition.getDefaultValue() != null){
            addError(new ValidationError(ValidationErrorType.DefaultForNonNullArgument));
        }
        if(variableDefinition.getDefaultValue() != null
                && !getValidationUtil().isValidLiteralValue(variableDefinition.getDefaultValue(),inputType)){
            addError(new ValidationError(ValidationErrorType.BadValueForDefaultArg));
        }
    }
}
