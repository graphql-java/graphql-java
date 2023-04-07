package graphql.validation;


import graphql.Internal;
import graphql.i18n.I18nMsg;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static graphql.validation.ValidationError.newValidationError;
import static java.lang.System.arraycopy;

@Internal
public class AbstractRule {

    private final ValidationContext validationContext;
    private final ValidationErrorCollector validationErrorCollector;
    private final ValidationUtil validationUtil;
    private boolean visitFragmentSpreads;

    public AbstractRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        this.validationContext = validationContext;
        this.validationErrorCollector = validationErrorCollector;
        this.validationUtil = new ValidationUtil();
    }

    public boolean isVisitFragmentSpreads() {
        return visitFragmentSpreads;
    }

    public void setVisitFragmentSpreads(boolean visitFragmentSpreads) {
        this.visitFragmentSpreads = visitFragmentSpreads;
    }

    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    public void addError(ValidationErrorType validationErrorType, Collection<? extends Node<?>> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node<?> node : locations) {
            locationList.add(node.getSourceLocation());
        }
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(locationList)
                .description(description));
    }

    public void addError(ValidationErrorType validationErrorType, SourceLocation location, String description) {
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(location)
                .description(description));
    }

    public void addError(ValidationError.Builder validationError) {
        validationErrorCollector.addError(validationError.queryPath(getQueryPath()).build());
    }

    public List<ValidationError> getErrors() {
        return validationErrorCollector.getErrors();
    }

    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public ValidationErrorCollector getValidationErrorCollector() {
        return validationErrorCollector;
    }

    protected List<String> getQueryPath() {
        return validationContext.getQueryPath();
    }

    /**
     * Creates an I18n message using the {@link graphql.i18n.I18nMsg}
     *
     * @param validationErrorType the type of validation failure
     * @param i18nMsg             the i18n message object
     *
     * @return the formatted I18n message
     */
    public String i18n(ValidationErrorType validationErrorType, I18nMsg i18nMsg) {
        return i18n(validationErrorType, i18nMsg.getMsgKey(), i18nMsg.getMsgArguments());
    }

    /**
     * Creates an I18N message using the key and arguments
     *
     * @param validationErrorType the type of validation failure
     * @param msgKey              the key in the underlying message bundle
     * @param msgArgs             the message arguments
     *
     * @return the formatted I18N message
     */
    public String i18n(ValidationErrorType validationErrorType, String msgKey, Object... msgArgs) {
        Object[] params = new Object[msgArgs.length + 1];
        params[0] = mkTypeAndPath(validationErrorType);
        arraycopy(msgArgs, 0, params, 1, msgArgs.length);

        return validationContext.i18n(msgKey, params);
    }

    private String mkTypeAndPath(ValidationErrorType validationErrorType) {
        List<String> queryPath = getQueryPath();
        StringBuilder sb = new StringBuilder();
        sb.append(validationErrorType);
        if (queryPath != null) {
            sb.append("@[").append(String.join("/", queryPath)).append("]");
        }
        return sb.toString();
    }

    public void checkDocument(Document document) {

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

    public void checkDirective(Directive directive, List<Node> ancestors) {

    }

    public void checkFragmentSpread(FragmentSpread fragmentSpread) {

    }

    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {

    }

    public void checkOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void leaveOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void checkSelectionSet(SelectionSet selectionSet) {

    }

    public void leaveSelectionSet(SelectionSet selectionSet) {

    }

    public void checkVariable(VariableReference variableReference) {

    }

    public void documentFinished(Document document) {

    }

    public void checkObjectValue(ObjectValue objectValue) {

    }

    @Override
    public String toString() {
        return "Rule{" + validationContext + "}";
    }
}
