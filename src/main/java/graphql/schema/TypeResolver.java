package graphql.schema;


import graphql.PublicSpi;
import graphql.TypeResolutionEnvironment;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This is called during type resolution to work out what concrete {@link graphql.schema.GraphQLObjectType} should be used
 * dynamically during runtime for {@link GraphQLInterfaceType}s and {@link GraphQLUnionType}s
 */
@NullMarked
@PublicSpi
public interface TypeResolver {

    /**
     * This call back is invoked passing in a context object to allow you to know what type to use
     * dynamically during runtime for {@link GraphQLInterfaceType}s and {@link GraphQLUnionType}s
     *
     * @param env the runtime environment
     *
     * @return a graphql object type to use based on examining the environment, or null if the type cannot be resolved
     */
    @Nullable GraphQLObjectType getType(TypeResolutionEnvironment env);

}
