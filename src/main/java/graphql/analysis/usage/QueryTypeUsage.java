package graphql.analysis.usage;

import graphql.PublicApi;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.SchemaPrinter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@PublicApi
public class QueryTypeUsage {

    private final Set<GraphQLType> allTypes = new LinkedHashSet<>();
    private final Set<GraphQLOutputType> outputTypes = new LinkedHashSet<>();
    private final Set<GraphQLInputType> inputTypes = new LinkedHashSet<>();
    private final GraphQLSchema graphQLSchema;

    QueryTypeUsage(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Set<GraphQLType> getAllTypes() {
        return allTypes;
    }

    public Set<GraphQLOutputType> getOutputTypes() {
        return outputTypes;
    }

    public Set<GraphQLInputType> getInputTypes() {
        return inputTypes;
    }

    public String printUsedTypes(SchemaPrinter.Options options) {
        return new SchemaPrinter(options).print(new ArrayList<>(allTypes));
    }

    public String printUsedTypes() {
        SchemaPrinter.Options options = SchemaPrinter.Options.defaultOptions()
                .includeDirectives(true)
                .useAstDefinitions(false);
        return printUsedTypes(options);
    }

    void addOutputReference(GraphQLOutputType type) {
        allTypes.add(type);
        outputTypes.add(type);
    }

    void addInputReference(GraphQLInputType type) {
        allTypes.add(type);
        inputTypes.add(type);
    }

}
