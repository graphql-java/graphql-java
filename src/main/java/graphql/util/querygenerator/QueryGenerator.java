package graphql.util.querygenerator;

import graphql.schema.*;

import javax.annotation.Nullable;
import java.util.*;
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

        if (!(type instanceof GraphQLOutputType)) {
            throw new IllegalArgumentException("Type " + typeName + " is not an output type");
        }

        return buildFields((GraphQLOutputType) type, new LinkedList<>());
    }

    private List<FieldData> buildFields(
            GraphQLOutputType type,
            Queue<FieldCoordinates> path
    ) {
        GraphQLOutputType unwrappedType = GraphQLTypeUtil.unwrapAllAs(type);

        if (unwrappedType instanceof GraphQLScalarType
                || unwrappedType instanceof GraphQLEnumType) {
            return null;
        }

        if (unwrappedType instanceof GraphQLObjectType) {
            List<GraphQLFieldDefinition> fields = ((GraphQLObjectType) unwrappedType).getFieldDefinitions();

            return fields.stream()
                    .map(fieldDef -> {
                        FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(
                                (GraphQLObjectType) unwrappedType,
                                fieldDef.getName()
                        );

                        if(path.contains(fieldCoordinates)) {
                            return null;
                        }

                        path.add(fieldCoordinates);

                        List<FieldData> fieldsData = buildFields(fieldDef.getType(), path);

                        path.remove();

                        // null fieldsData means that the field is a scalar or enum type
                        // empty fieldsData means that the field is a type, but all its fields were filtered out
                        if(fieldsData != null && fieldsData.isEmpty())  {
                            return null;
                        }

                        return  new FieldData(fieldDef.getName(), fieldsData);
                    })
                    .filter(Objects::nonNull)
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
