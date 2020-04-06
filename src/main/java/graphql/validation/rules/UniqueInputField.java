package graphql.validation.rules;

import graphql.language.Argument;
import graphql.language.Field;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.HashSet;
import java.util.Set;

public class UniqueInputField extends AbstractRule {
    public UniqueInputField(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        if(field.getArguments()==null||field.getArguments().size()<=1){
            return;
        }

        Set<String> arguments=new HashSet<>(field.getArguments().size());

        for (Argument argument : field.getArguments()) {
            if(arguments.contains(argument.getName())){
                addError(ValidationErrorType.DuplicateInputField, field.getSourceLocation(), duplicateInputFieldMessage(argument.getName()));
            }else{
                arguments.add(argument.getName());
            }
        }
    }

    static String duplicateInputFieldMessage(String definitionName) {
        return String.format("There can be only one argument named '%s'", definitionName);
    }
}
