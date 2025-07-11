package graphql.normalized;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * A {@link GraphQlNormalizedField} represents a normalized field in GraphQL.
 */
@PublicApi
public interface GraphQlNormalizedField {
    /**
     * @return the children of the {@link GraphQlNormalizedField}
     */
    List<GraphQlNormalizedField> getGraphQlNormalizedChildren();

    /**
     * @return the parent of this {@link GraphQlNormalizedField} or null if it's a top level field
     */
    GraphQlNormalizedField getGraphQlNormalizedParent();

    /**
     * A {@link GraphQlNormalizedField} can sometimes (for non-concrete types like interfaces and unions)
     * have more than one object type it could be when executed.  There is no way to know what it will be until
     * the field is executed over data and the type is resolved via a {@link graphql.schema.TypeResolver}.
     * <p>
     * This method returns all the possible types a field can be which is one or more {@link GraphQLObjectType}
     * names.
     * <p>
     * Warning: This returns a Mutable Set. No defensive copy is made for performance reasons.
     *
     * @return a set of the possible type names this field could be.
     */
    Set<String> getObjectTypeNames();

    /**
     * @return the field alias used or null if there is none
     *
     * @see #getResultKey()
     * @see #getName()
     */
    String getAlias();

    /**
     * All merged fields have the same name so this is the name of the {@link GraphQlNormalizedField}.
     * <p>
     * WARNING: This is not always the key in the execution result, because of possible field aliases.
     *
     * @return the name of this {@link GraphQlNormalizedField}
     *
     * @see #getResultKey()
     * @see #getAlias()
     */
    String getName();

    /**
     * @return true if this field has children, false otherwise
     */
    boolean hasChildren();

    /**
     * @return a helper method to show the object types names as a string
     */
    String objectTypeNamesToString();

    /**
     * Returns the field definitions for this field in the given schema.
     * @param schema the GraphQL schema to look up the field definitions
     * @return a list of field definitions for this field
     */
    List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLSchema schema);

    /**
     * @return the GraphQL output type for this field
     */
    GraphQLOutputType getType(GraphQLSchema schema);

    /**
     * @return a map of the resolved argument values
     */
    LinkedHashMap<String, Object> getResolvedArguments();

    /**
     * the level of the {@link GraphQlNormalizedField} in the operation hierarchy with top level fields
     * starting at 1
     *
     * @return the level of the {@link GraphQlNormalizedField} in the operation hierarchy
     */
    int getLevel();

    /**
     * Determines whether this {@link GraphQlNormalizedField} needs a fragment to select the field. However, it considers the parent
     * output type when determining whether it needs a fragment.
     * <p>
     * Consider the following schema
     *
     * <pre>
     * interface Animal {
     *     name: String
     *     parent: Animal
     * }
     * type Cat implements Animal {
     *     name: String
     *     parent: Cat
     * }
     * type Dog implements Animal {
     *     name: String
     *     parent: Dog
     *     isGoodBoy: Boolean
     * }
     * type Query {
     *     animal: Animal
     * }
     * </pre>
     * <p>
     * and the following query
     *
     * <pre>
     * {
     *     animal {
     *         parent {
     *             name
     *         }
     *     }
     * }
     * </pre>
     * <p>
     * Then we would get the following {@link GraphQlNormalizedOperation}
     *
     * <pre>
     * -Query.animal: Animal
     * --[Cat, Dog].parent: Cat, Dog
     * ---[Cat, Dog].name: String
     * </pre>
     * <p>
     * If we simply checked the parent's {@link #getFieldDefinitions(GraphQLSchema)} that would
     * point us to {@code Cat.parent} and {@code Dog.parent} whose output types would incorrectly answer
     * our question whether this is conditional?
     * <p>
     * We MUST consider that the output type of the {@code parent} field is {@code Animal} and
     * NOT {@code Cat} or {@code Dog} as their respective implementations would say.
     *
     * @param schema - the graphql schema in play
     *
     * @return true if the field is conditional
     */
    boolean isConditional(@NonNull GraphQLSchema schema);

    /**
     * Returns the result key of this {@link GraphQlNormalizedField} within the overall result.
     * This is either a field alias or the value of {@link #getName()}
     *
     * @return the result key for this {@link GraphQlNormalizedField}.
     *
     * @see #getName()
     */
    String getResultKey();
}
