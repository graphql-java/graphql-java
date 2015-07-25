package graphql.validation;


import graphql.language.*;

import java.util.List;

public class AbstractRule {

    private final ValidationContext validationContext;
    private final ValidationErrorCollector validationErrorCollector;

    private ValidationUtil validationUtil = new ValidationUtil();

    public AbstractRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        this.validationContext = validationContext;
        this.validationErrorCollector = validationErrorCollector;
    }


    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    public void setValidationUtil(ValidationUtil validationUtil) {
        this.validationUtil = validationUtil;
    }

    public void addError(ValidationError error){
        validationErrorCollector.addError(error);
    }

    public List<ValidationError> getErrors(){
        return validationErrorCollector.getErrors();
    }


    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public void checkArgument(Argument argument) {

    }

    public void checkTypeName(TypeName typeName) {

    }

    public void checkVariableDefinition(VariableDefinition variableDefinition) {

    }

    public void checkField(Field field) {

    }

    public void checkInlineFragment(InlineFragment inlineFragment) {

    }

    public void checkDirective(Directive directive,List<Node> ancestors) {

    }

    public void checkFragmentSpread(FragmentSpread fragmentSpread) {

    }

    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {

    }

    public void checkOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void checkSelectionSet(SelectionSet selectionSet) {

    }

    public void checkVariable(VariableReference variableReference) {

    }

    public void documentFinished(Document document){

    }


}
