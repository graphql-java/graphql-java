package graphql.execution.conversion;

import graphql.PublicApi;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLTypeUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * This creates a series of {@link graphql.execution.conversion.ArgumentConverter}s keyed by input type name allowing you to register a
 * series of argument converters and then place it into the conversion mix via
 * {@link graphql.schema.GraphQLCodeRegistry.Builder#argumentConverter(ArgumentConverter)}.
 *
 * If the input type matches the specified name, then its argument converter will be called otherwise the source object will be
 * return as is.
 */
@PublicApi
public class ArgumentConversions implements ArgumentConverter {

    private final Map<String, ArgumentConverter> typeToConverterMap;

    private ArgumentConversions(Map<String, ArgumentConverter> typeToConverterMap) {
        this.typeToConverterMap = typeToConverterMap;
    }

    @Override
    public Object convertArgument(ArgumentConverterEnvironment environment) {
        String inputTypeName = GraphQLTypeUtil.unwrapAll(environment.getArgumentType()).getName();
        ArgumentConverter argumentConverter = typeToConverterMap.get(inputTypeName);
        if (argumentConverter == null) {
            return environment.getValueToBeConverted();
        }
        return argumentConverter.convertArgument(environment);
    }

    public static Builder newConversions() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, ArgumentConverter> typeToConverterMap = new LinkedHashMap<>();

        /**
         * Registers an argument converter against the specified input type
         *
         * @param inputTypeName     the input type
         * @param argumentConverter the non null argument converter
         *
         * @return this builder
         */
        public Builder converter(String inputTypeName, ArgumentConverter argumentConverter) {
            typeToConverterMap.put(assertNotNull(inputTypeName), assertNotNull(argumentConverter));
            return this;
        }

        /**
         * Registers an argument converter against the specified input type
         *
         * @param inputType         the input type
         * @param argumentConverter the non null argument converter
         *
         * @return this builder
         */
        public Builder converter(GraphQLInputType inputType, ArgumentConverter argumentConverter) {
            typeToConverterMap.put(assertNotNull(inputType).getName(), assertNotNull(argumentConverter));
            return this;
        }

        /**
         * Registers the map of argument converters against the specified input types
         *
         * @param argumentConverters a map of input type names to argument converters
         *
         * @return this builder
         */
        public Builder converters(Map<String, ArgumentConverter> argumentConverters) {
            assertNotNull(argumentConverters).forEach((k, v) ->
                    typeToConverterMap.put(assertNotNull(k), assertNotNull(v)));
            return this;
        }

        public ArgumentConversions build() {
            return new ArgumentConversions(typeToConverterMap);
        }
    }
}
