Scalars in graphql
==================

Scalars
-------

The leaf nodes of the graphql type system are called scalars.  Once you reach a scalar type you
cannot descend down any further into the type hierarchy.  A scalar type is meant to represent
an indivisible value.

The graphql specification says that all implementations must have the following scalar types.

* String aka ``GraphQLString`` - A UTF‐8 character sequence.
* Boolean aka ``GraphQLBoolean`` - true or false.
* Int aka ``GraphQLInt`` - A signed 32‐bit integer.
* Float aka ``GraphQLFloat`` - A signed double-precision floating-point value.
* ID aka ``GraphQLID`` - A unique identifier which is serialized in the same way as a String; however, defining it as an ID signifies that it is not intended to be human‐readable.

graphql-java adds the following scalar types which are useful in Java based systems

* Long aka ``GraphQLLong`` - a java.lang.Long based scalar
* Short aka ``GraphQLShort`` - a java.lang.Short based scalar
* Byte aka ``GraphQLByte``  - a java.lang.Byte based scalar
* BigDecimal aka ``GraphQLBigDecimal`` - a java.math.BigDecimal based scalar
* BigInteger aka ``GraphQLBigInteger`` - a java.math.BigInteger based scalar


The class ``graphql.Scalars`` contains singleton instances of the provided scalar types

Writing your Own Custom Scalars
-------------------------------

You can write your own custom scalar implementations.  In doing so you take on the responsibility for coercing values
at runtime, which we will explain in a moment.

Imagine we decide we need to have an email scalar type.  It will take email addresses as input and output.

We would create a singleton ``graphql.schema.GraphQLScalarType`` instance for this like so.

.. code-block:: java

        public static final GraphQLScalarType EMAIL = new GraphQLScalarType("email", "A custom scalar that handles emails", new Coercing() {
            @Override
            public Object serialize(Object dataFetcherResult) {
                return serializeEmail(dataFetcherResult);
            }

            @Override
            public Object parseValue(Object input) {
                return parseEmailFromVariable(input);
            }

            @Override
            public Object parseLiteral(Object input) {
                return parseEmailFromAstLiteral(input);
            }
        });


Coercing values
---------------

The real work in any custom scalar implementation is the ``graphql.schema.Coercing`` implementation.  This is responsible for 3 functions

* ``parseValue`` - takes a variable input object and converts into the Java runtime representation
* ``parseLiteral`` - takes an AST literal ``graphql.language.Value` as input and converts into the Java runtime representation
* ``serialize`` - takes a Java object and converts it into the output shape for that scalar

So your custom scalar code has to handle 2 forms of input (parseValue / parseLiteral)  and 1 form of output (serialize).

Imagine this query, which uses variables, AST literals and outputs our scalar type ```email``.

.. code-block:: graphql

    mutation Contact($mainContact: Email!) {
      makeContact(mainContactEmail: $mainContact, backupContactEmail: "backup@company.com") {
        id
        mainContactEmail
      }
    }

Our custom Email scalar will

* be called via ``parseValue`` to convert the ``$mainContact`` variable value into a runtime object
* be called via ``parseLiteral`` to convert the AST ``graphql.language.StringValue`` "backup@company.com" into a runtime object
* be called via ``serialise`` to turn the runtime representation of mainContactEmail into a form ready for output

Validation of input and output
------------------------------

The methods can validate that the received input makes sense.  For example our email scalar will try to validate that the input
and output are indeed email addresses.

The JavaDoc method contract of ``graphql.schema.Coercing`` says the following

* The ``serialise`` MUST ONLY allow ``graphql.schema.CoercingSerializeException`` to be thrown from it.  This indicates that the
value cannot be serialised into an appropriate form.  You must not allow other runtime exceptions to escape this method to get
the normal graphql behaviour for validation.  You MUST return a non null value


* The ``parseValue`` MUST ONLY allow ``graphql.schema.CoercingParseValueException`` to be thrown from it.  This indicates that the
value cannot be parsed as input into an appropriate form.  You must not allow other runtime exceptions to escape this method to get
the normal graphql behaviour for validation.  You MUST return a non null value.

* The ``parseLiteral``  MUST ONLY allow ``graphql.schema.CoercingParseLiteralException`` to be thrown from it.  This indicates that the
AST value cannot be parsed as input into an appropriate form.  You must not allow any runtime exceptions to escape this method to get
the normal graphql behaviour for validation.

Some people try to rely on runtime exceptions for validation and hope that they come out as graphql errors.  This is not the case.  You
MUST follow the ``Coercing`` method contracts to allow the graphql-java engine to work according to the graphql specification on scalar types.

Example implementation
----------------------

The following is a really rough implementation of our imagined ``email`` scalar type to show you how one might implement the ``Coercing`` methods
such a scalar.

.. code-block:: java

    public static class EmailScalar {

        public static final GraphQLScalarType EMAIL = new GraphQLScalarType("email", "A custom scalar that handles emails", new Coercing() {
            @Override
            public Object serialize(Object dataFetcherResult) {
                return serializeEmail(dataFetcherResult);
            }

            @Override
            public Object parseValue(Object input) {
                return parseEmailFromVariable(input);
            }

            @Override
            public Object parseLiteral(Object input) {
                return parseEmailFromAstLiteral(input);
            }
        });


        private static boolean looksLikeAnEmailAddress(String possibleEmailValue) {
            // ps.  I am not trying to replicate RFC-3696 clearly
            return Pattern.matches("[A-Za-z0-9]@[.*]", possibleEmailValue);
        }

        private static Object serializeEmail(Object dataFetcherResult) {
            String possibleEmailValue = String.valueOf(dataFetcherResult);
            if (looksLikeAnEmailAddress(possibleEmailValue)) {
                return possibleEmailValue;
            } else {
                throw new CoercingSerializeException("Unable to serialize " + possibleEmailValue + " as an email address");
            }
        }

        private static Object parseEmailFromVariable(Object input) {
            if (input instanceof String) {
                String possibleEmailValue = input.toString();
                if (looksLikeAnEmailAddress(possibleEmailValue)) {
                    return possibleEmailValue;
                }
            }
            throw new CoercingParseValueException("Unable to parse variable value " + input + " as an email address");
        }

        private static Object parseEmailFromAstLiteral(Object input) {
            if (input instanceof StringValue) {
                String possibleEmailValue = ((StringValue) input).getValue();
                if (looksLikeAnEmailAddress(possibleEmailValue)) {
                    return possibleEmailValue;
                }
            }
            throw new CoercingParseLiteralException(
                    "Value is not any email address : '" + String.valueOf(input) + "'"
            );
        }
    }

