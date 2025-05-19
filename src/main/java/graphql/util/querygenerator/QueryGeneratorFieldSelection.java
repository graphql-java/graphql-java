package graphql.util.querygenerator;

import graphql.schema.*;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class QueryGeneratorFieldSelection {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;

    public QueryGeneratorFieldSelection(QueryGeneratorOptions options) {
        this.options = options;
        this.schema = options.getSchema();
    }

    public List<FieldSelection> generateFieldSelection(String typeName) {
        GraphQLType type = this.schema.getType(typeName);

        if (type == null) {
            throw new IllegalArgumentException("Type " + typeName + " not found in schema");
        }

        if (!(type instanceof GraphQLOutputType)) {
            throw new IllegalArgumentException("Type " + typeName + " is not an output type");
        }

        return buildFields((GraphQLOutputType) type, new Stack<>());
    }

    private List<FieldSelection> buildFields(
            GraphQLOutputType type,
            Stack<FieldCoordinates> visited
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

                        if(visited.contains(fieldCoordinates)) {
                            // TODO: maybe add 'cyclicDependencyIdentified' to the result
                            System.out.println("Cycle detected: " + fieldCoordinates);
                            return null;
                        }

                        visited.add(fieldCoordinates);

                        List<FieldSelection> fieldsData = buildFields(fieldDef.getType(), visited);

                        FieldCoordinates polled = visited.pop();

                        if(polled != fieldCoordinates) {
                            System.out.println("Unexpected field coordinates: " + polled);
                        }

                        // null fieldsData means that the field is a scalar or enum type
                        // empty fieldsData means that the field is a type, but all its fields were filtered out
                        if(fieldsData != null && fieldsData.isEmpty())  {
                            return null;
                        }

                        return  new FieldSelection(fieldDef.getName(), fieldsData);
                    })
                    .filter(Objects::nonNull)
                    .collect(toList());
        }

        throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
    }

    public static class FieldSelection {
        public final String name;
        public final List<FieldSelection> fields;

        public FieldSelection(String name, List<FieldSelection> fields) {
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
