package graphql.util.querygenerator;

import graphql.schema.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class QueryGenerator {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;

    public QueryGenerator(QueryGeneratorOptions options) {
        this.options = options;
        this.schema = options.getSchema();
    }

    public List<FieldData> generateQuery(String typeName) {
        GraphQLType type = this.schema.getType(typeName);

        if (type == null) {
            throw new IllegalArgumentException("Type " + typeName + " not found in schema");
        }

        if(!(type instanceof GraphQLOutputType)) {
            throw new IllegalArgumentException("Type " + typeName + " is not an output type");
        }

        return buildFields((GraphQLOutputType) type);
    }

    private List<FieldData> buildFields(
            GraphQLOutputType type
    ) {
        GraphQLOutputType unwrappedType = GraphQLTypeUtil.unwrapAllAs(type);

        if (unwrappedType instanceof GraphQLScalarType
         || unwrappedType instanceof GraphQLEnumType) {
            return null;
        }

        if (unwrappedType instanceof GraphQLObjectType) {
            List<GraphQLFieldDefinition> fields = ((GraphQLObjectType) unwrappedType).getFieldDefinitions();

            return fields.stream()
                    .map(fieldDef ->
                            new FieldData(fieldDef.getName(), buildFields(fieldDef.getType())))
                    .collect(toList());
        }

        throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
    }

    public static class FieldData {
        public final String name;
        public final List<FieldData> fields;

        public FieldData(String name, List<FieldData> fields) {
            this.name = name;
            this.fields = fields;
        }

    }

    public static class QueryGeneratorResult {

    }

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder builder() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder();
    }

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder defaultOptions() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder()
                .maxDepth(5);
    }
}
