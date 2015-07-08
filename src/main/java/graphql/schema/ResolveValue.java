package graphql.schema;


import java.util.List;

public interface ResolveValue {

    Object resolve(Object source, List<Object> arguments);
}
