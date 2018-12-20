package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedFields;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A map from name to List of Field representing the actual sub selections (during execution) of a Field with Fragments
 * evaluated and conditional directives considered.
 */
@Internal
public class FieldSubSelection {

    private Object source;
    // the type of this must be objectType
    private ExecutionStepInfo executionInfo;
    private Map<String, MergedFields> fields = new LinkedHashMap<>();

    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public Map<String, MergedFields> getFields() {
        return fields;
    }

    public void setFields(Map<String, MergedFields> fields) {
        this.fields = fields;
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
                ", fields=" + fields +
                '}';
    }

    public String toShortString() {
        return "FieldSubSelection{" +
                "fields=" + fields.keySet() +
                '}';
    }

}
