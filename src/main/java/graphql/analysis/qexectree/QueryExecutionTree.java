package graphql.analysis.qexectree;

import graphql.PublicApi;

import java.util.List;

@PublicApi
public class QueryExecutionTree {

   private final List<QueryExecutionField> rootFields;

   public QueryExecutionTree(List<QueryExecutionField> rootFields) {
      this.rootFields = rootFields;
   }

   public List<QueryExecutionField> getRootFields() {
      return rootFields;
   }
}
