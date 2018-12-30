package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;

import java.util.Map;


/**
 * A map from name to List of Field representing the actual sub selections (during execution) of a Field with Fragments
 * evaluated and conditional directives considered.
 */
@Internal
public class FieldSubSelection {

    private Object source;
    // the type of this must be objectType and is the parent executionStepInfo for all mergedSelectionSet
    private ExecutionStepInfo executionInfo;
    private MergedSelectionSet mergedSelectionSet;

    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public Map<String, MergedField> getSubFields() {
        return mergedSelectionSet.getSubFields();
    }

    public MergedSelectionSet getMergedSelectionSet() {
        return mergedSelectionSet;
    }

    public void setMergedSelectionSet(MergedSelectionSet mergedSelectionSet) {
        this.mergedSelectionSet = mergedSelectionSet;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionInfo;
    }

    public void setExecutionStepInfo(ExecutionStepInfo executionInfo) {
        this.executionInfo = executionInfo;
    }

    @Override
    public String toString() {
        return "FieldSubSelection{" +
                "source=" + source +
                ", executionInfo=" + executionInfo +
                ", mergedSelectionSet" + mergedSelectionSet +
                '}';
    }

    public String toShortString() {
        return "FieldSubSelection{" +
                "fields=" + mergedSelectionSet.getSubFields().keySet() +
                '}';
    }

}
