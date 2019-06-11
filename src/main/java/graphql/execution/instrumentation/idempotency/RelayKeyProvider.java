package graphql.execution.instrumentation.idempotency;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.GraphQLFieldDefinition;
import java.util.List;
import java.util.Map;

/**
 * {@link KeyProvider] implementation that assumes Relay-compliant mutations and uses the value of
 * the <code>clientMutationId</code> input field as the idempotency key (cf.
 * https://facebook.github.io/relay/graphql/mutations.htm).
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public class RelayKeyProvider implements KeyProvider {

  private static final String CLIENT_MUTATION_ID = "clientMutationId";

  @Override
  public String getKeyFromOperation(ExecutionContext context) {
    final OperationDefinition op = context.getOperationDefinition();
    if (op == null) {
      return null;
    }
    final List<Selection> selections = op.getSelectionSet().getSelections();
    if (selections == null || selections.isEmpty() || !(selections.get(0) instanceof Field)) {
      return null;
    }
    final Field selection = (Field) selections.get(0);
    final List<Argument> arguments = selection.getArguments();
    if (arguments == null || arguments.isEmpty()) {
      return null;
    }
    final Value argValue = selection.getArguments().get(0).getValue();
    if (!(argValue instanceof ObjectValue)) {
      return null;
    }
    final List<ObjectField> fields = ((ObjectValue) argValue).getObjectFields();
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    for (ObjectField field : fields) {
      final String clientMutationId = getClientMutationId(field, context);
      if (clientMutationId != null) {
        return clientMutationId;
      }
    }
    return null;
  }

  @Override
  public String getKeyFromField(ExecutionContext context, GraphQLFieldDefinition fieldDefinition,
      ExecutionStepInfo typeInfo) {
    if (typeInfo == null) {
      return null;
    }
    final MergedField field = typeInfo.getField();
    if (field == null) {
      return null;
    }
    final List<Argument> arguments = field.getArguments();
    if (arguments == null || arguments.isEmpty()) {
      return null;
    }
    final Value<?> value = arguments.get(0).getValue();
    if (value == null || value == NullValue.Null) {
      return null;
    }
    final List<Node> children = value.getChildren();
    for (Node<?> child : children) {
      final String clientMutationId = getClientMutationId(child, context);
      if (clientMutationId != null) {
        return clientMutationId;
      }
    }
    return null;
  }

  private static String getClientMutationId(Node<?> node, ExecutionContext context) {
    if (!(node instanceof ObjectField)) {
      return null;
    }
    final ObjectField field = (ObjectField) node;
    final Value argValue = field.getValue();
    if (CLIENT_MUTATION_ID.equals(field.getName())
        && argValue != null
        && argValue != NullValue.Null) {
      if (argValue instanceof StringValue) {
        return ((StringValue) argValue).getValue();
      } else if (argValue instanceof VariableReference) {
        final Map<String, Object> variables = context.getVariables();
        final Object value =
            variables == null ? null : variables.get(((VariableReference) argValue).getName());
        return value == null ? null : value.toString();
      }
    }
    return null;
  }

}
