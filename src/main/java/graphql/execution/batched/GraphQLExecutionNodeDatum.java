//package graphql.execution.batched;
//
//import java.util.Map;
//
//class GraphQLExecutionNodeDatum extends ResultContainer {
//
//    public GraphQLExecutionNodeDatum(Map<String, Object> parentResult, Object source) {
//        this.parentResult = parentResult;
//        this.source = source;
//    }
//
//    public Map<String, Object> getParentResult() {
//        return parentResult;
//    }
//
//
//    @Override
//    public void putResult(String fieldName, Object value) {
//        parentResult.put(fieldName, value);
//    }
//
//    public Object getSource() {
//        return source;
//    }
//}
