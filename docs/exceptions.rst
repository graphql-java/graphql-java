Runtime Exceptions
==================


Runtime exceptions can be thrown by the graphql engine if certain exceptional situations are encountered.  The following
are a list of the exceptions that can be thrown all the way out of a ``graphql.execute(...)`` call.

These are not graphql errors in execution but rather totally unacceptable conditions in which to execute a graphql query.
 
 -  `graphql.schema.CoercingSerializeException`

 is thrown when a value cannot be serialised by a Scalar type, for example
 a String value being coerced as an Int.


 -  `graphql.schema.CoercingParseValueException`

 is thrown when a value cannot be parsed by a Scalar type, for example
 a String input value being parsed as an Int.


 -  `graphql.execution.UnresolvedTypeException`

 is thrown if a  graphql.schema.TypeResolver` fails to provide a concrete
 object type given a interface or union type.


 -  `graphql.execution.NonNullableValueCoercedAsNullException`

 is thrown if a non null variable argument is coerced as a
 null value during execution.


 -  `graphql.execution.InputMapDefinesTooManyFieldsException`

 is thrown if a map used for an input type object contains
 more keys than is defined in that input type.


 -  `graphql.schema.validation.InvalidSchemaException`

 is thrown if the schema is not valid when built via
  graphql.schema.GraphQLSchema.Builder#build()`

 -  `graphql.execution.UnknownOperationException`

if multiple operations are defined in the query and
the operation name is missing or there is no matching operation name
contained in the GraphQL query.

 -  `graphql.GraphQLException`

 is thrown as a general purpose runtime exception, for example if the code cant
 access a named field when examining a POJO, it is analogous to a RuntimeException if you will.


 -  `graphql.AssertException`

 is thrown as a low level code assertion exception for truly unexpected code conditions, things we assert
should never happen in practice.

