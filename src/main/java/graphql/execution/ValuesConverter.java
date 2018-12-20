package graphql.execution;

import graphql.Internal;
import graphql.execution.conversion.ArgumentConverter;
import graphql.execution.conversion.ArgumentConverterEnvironmentImpl;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;

import java.util.List;

@Internal
public class ValuesConverter {

    public Object convertValue(Object value, GraphQLCodeRegistry codeRegistry, GraphQLArgument fieldArgument) {
        ArgumentConverterEnvironmentImpl environment = ArgumentConverterEnvironmentImpl.newEnvironment()
                .sourceObject(value).argument(fieldArgument).build();
        List<ArgumentConverter> argumentConverters = codeRegistry.getArgumentConverters();
        Object newValue = value;
        for (ArgumentConverter argumentConverter : argumentConverters) {
            newValue = argumentConverter.convertArgument(environment);
            // direct object compare - the first converter to actually change the object wins
            if (newValue != value) {
                break;
            }
        }
        return newValue;
    }
}
