package graphql.schema.idl;

import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

@Internal
@NullMarked
public interface SchemaPrintAccess {

    Object getSchema();

    Object getQueryType();

    @Nullable Object getMutationType();

    @Nullable Object getSubscriptionType();

    List<Object> getTypes();

    List<Object> getDirectiveDefinitions();

    SchemaPrintElementKind getKind(Object element);

    String getName(Object element);

    @Nullable String getDescription(Object element);

    @Nullable String getAstDefinitionComments(Object element);

    boolean isIncluded(Object element);

    boolean isIntrospectionType(Object type);

    boolean isSpecifiedScalar(Object type);

    List<Object> getFields(Object type);

    List<Object> getArguments(Object fieldOrDirective);

    Object getType(Object typedElement);

    List<Object> getInterfaces(Object type);

    List<Object> getUnionMembers(Object unionType);

    List<Object> getEnumValues(Object enumType);

    List<Object> getInputFields(Object inputObjectType);

    List<Object> getAppliedDirectives(Object container);

    List<Object> getAppliedDirectiveArguments(Object directive);

    InputValueWithState getDefaultValue(Object argumentOrInputField);

    InputValueWithState getAppliedDirectiveArgumentValue(Object argument);

    boolean isRepeatable(Object directive);

    Set<DirectiveLocation> getDirectiveLocations(Object directive);

    @Nullable String getSpecifiedByUrl(Object scalar);

    String getTypeString(Object type);

    String printValue(InputValueWithState value, Object type);

    List<Object> sort(
            @Nullable Object parent,
            SchemaPrintChildKind childKind,
            List<Object> elements);
}
