package graphql.util;

import graphql.ExperimentalApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTraverser;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Finds all cycles in a GraphQL Schema.
 * Cycles caused by built-in introspection types are filtered out.
 */
@ExperimentalApi
public class CyclicSchemaAnalyzer {

    public static class SchemaElementWithChild {
        private final GraphQLSchemaElement element;
        private final int childIndex;

        public SchemaElementWithChild(GraphQLSchemaElement element, int childIndex) {
            this.element = element;
            this.childIndex = childIndex;
        }
    }

    public static class SchemaCycle {
        private final List<SchemaElementWithChild> cycleElements = new ArrayList<>();

        public String toString() {
            StringJoiner result = new StringJoiner(" -> ");
            for (SchemaElementWithChild schemaElementWithChild : cycleElements) {
                result.add(GraphQLTypeUtil.simplePrint(schemaElementWithChild.element) + "/" + schemaElementWithChild.childIndex);
            }
            return result.toString();
        }

        public int size() {
            return cycleElements.size();
        }
    }

    public static List<SchemaCycle> findCycles(GraphQLSchema schema) {
        SchemaTraverser traverser = new SchemaTraverser();
        Visitor visitor = new Visitor();
        traverser.depthFirstFullSchema(visitor, schema);
        List<SchemaCycle> result = new ArrayList<>();
        for (List<Breadcrumb<GraphQLSchemaElement>> possibleCycle : visitor.cycles) {
            SchemaCycle schemaCycle = new SchemaCycle();
            // the breadcrumbs are in the reverse order of the dependency
            for (int i = possibleCycle.size() - 1; i >= 0; i--) {
                Breadcrumb<GraphQLSchemaElement> breadcrumb = possibleCycle.get(i);
                SchemaElementWithChild schemaElementWithChild = new SchemaElementWithChild(breadcrumb.getNode(), breadcrumb.getLocation().getIndex());
                schemaCycle.cycleElements.add(schemaElementWithChild);
            }
            result.add(schemaCycle);
        }
        return result;
    }

    private static class Visitor extends GraphQLTypeVisitorStub {
        public final List<List<Breadcrumb<GraphQLSchemaElement>>> cycles = new ArrayList<>();

        @Override
        public TraversalControl visitBackRef(TraverserContext<GraphQLSchemaElement> context) {
            List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs = context.getBreadcrumbs();

            for (int i = breadcrumbs.size() - 1; i >= 0; i--) {
                Breadcrumb<GraphQLSchemaElement> breadcrumb = breadcrumbs.get(i);
                if (breadcrumb.getNode() instanceof GraphQLNamedType && Introspection.isIntrospectionTypes((GraphQLNamedType) breadcrumb.getNode())) {
                    return TraversalControl.CONTINUE;
                }
                if (breadcrumb.getNode() == context.thisNode()) {
                    List<Breadcrumb<GraphQLSchemaElement>> cycle = breadcrumbs.subList(0, i + 1);
                    cycles.add(cycle);
                }
            }
            return TraversalControl.CONTINUE;
        }
    }


}
