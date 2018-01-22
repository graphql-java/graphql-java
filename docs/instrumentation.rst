Instrumentation
===============


The ``graphql.execution.instrumentation.Instrumentation`` interface allows you to inject code that can observe the
execution of a query and also change the runtime behaviour.

The primary use case for this is to allow say performance monitoring and custom logging but it could be used for many different purposes.

When you build the ```Graphql`` object you can specify what ``Instrumentation`` to use (if any).


.. code-block:: java

        GraphQL.newGraphQL(schema)
                .instrumentation(new TracingInstrumentation())
                .build();

Custom Instrumentation
----------------------

An implementation of ``Instrumentation`` needs to implement the "begin" step methods that represent the execution of a graphql query.

Each step must give back a non null ``graphql.execution.instrumentation.InstrumentationContext`` object which will be called back
when the step completes, and will be told that it succeeded or failed with a Throwable.

The following is a basic custom ``Instrumentation`` that measures overall execution time and puts it into a stateful object.

.. code-block:: java

    class CustomInstrumentationState implements InstrumentationState {
        private Map<String, Object> anyStateYouLike = new HashMap<>();

        void recordTiming(String key, long time) {
            anyStateYouLike.put(key, time);
        }
    }

    class CustomInstrumentation extends SimpleInstrumentation {
        @Override
        public InstrumentationState createState() {
            //
            // instrumentation state is passed during each invocation of an Instrumentation method
            // and allows you to put stateful data away and reference it during the query execution
            //
            return new CustomInstrumentationState();
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
            long startNanos = System.nanoTime();
            return new SimpleInstrumentationContext<ExecutionResult>() {
                @Override
                public void onCompleted(ExecutionResult result, Throwable t) {
                    CustomInstrumentationState state = parameters.getInstrumentationState();
                    state.recordTiming(parameters.getQuery(), System.nanoTime() - startNanos);
                }
            };
        }

        @Override
        public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
            //
            // this allows you to intercept the data fetcher used to fetch a field and provide another one, perhaps
            // that enforces certain behaviours or has certain side effects on the data
            //
            return dataFetcher;
        }

        @Override
        public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
            //
            // this allows you to instrument the execution result some how.  For example the Tracing support uses this to put
            // the `extensions` map of data in place
            //
            return CompletableFuture.completedFuture(executionResult);
        }
    }

Chaining Instrumentation
------------------------

You can combine multiple ``Instrumentation`` objects together using the ``graphql.execution.instrumentation.ChainedInstrumentation`` class which
accepts a list of ``Instrumentation`` objects and calls them in that defined order.

.. code-block:: java

        List<Instrumentation> chainedList = new ArrayList<>();
        chainedList.add(new FooInstrumentation());
        chainedList.add(new BarInstrumentation());
        ChainedInstrumentation chainedInstrumentation = new ChainedInstrumentation(chainedList);

        GraphQL.newGraphQL(schema)
                .instrumentation(chainedInstrumentation)
                .build();



Apollo Tracing Instrumentation
------------------------------

``graphql.execution.instrumentation.tracing.TracingInstrumentation`` is an ``Instrumentation`` implementation that creates tracing information
about the query that is being executed.

It follows the Apollo proposed tracing format defined at `https://github.com/apollographql/apollo-tracing <https://github.com/apollographql/apollo-tracing>`_

A detailed tracing map will be created and placed in the ``extensions`` section of the result.

So given a query like

.. code-block:: graphql

    query {
      hero {
        name
        friends {
          name
        }
      }
    }

It would return a result like

.. code-block:: json

    {
      "data": {
        "hero": {
          "name": "R2-D2",
          "friends": [
            {
              "name": "Luke Skywalker"
            },
            {
              "name": "Han Solo"
            },
            {
              "name": "Leia Organa"
            }
          ]
        }
      },
      "extensions": {
        "tracing": {
          "version": 1,
          "startTime": "2017-08-14T23:13:39.362Z",
          "endTime": "2017-08-14T23:13:39.497Z",
          "duration": 135589186,
          "execution": {
            "resolvers": [
              {
                "path": [
                  "hero"
                ],
                "parentType": "Query",
                "returnType": "Character",
                "fieldName": "hero",
                "startOffset": 105697585,
                "duration": 79111240
              },
              {
                "path": [
                  "hero",
                  "name"
                ],
                "parentType": "Droid",
                "returnType": "String",
                "fieldName": "name",
                "startOffset": 125010028,
                "duration": 20213
              },
              {
                "path": [
                  "hero",
                  "friends"
                ],
                "parentType": "Droid",
                "returnType": "[Character]",
                "fieldName": "friends",
                "startOffset": 133352819,
                "duration": 7927560
              },
              {
                "path": [
                  "hero",
                  "friends",
                  0,
                  "name"
                ],
                "parentType": "Human",
                "returnType": "String",
                "fieldName": "name",
                "startOffset": 134105887,
                "duration": 6783
              },
              {
                "path": [
                  "hero",
                  "friends",
                  1,
                  "name"
                ],
                "parentType": "Human",
                "returnType": "String",
                "fieldName": "name",
                "startOffset": 134725922,
                "duration": 7016
              },
              {
                "path": [
                  "hero",
                  "friends",
                  2,
                  "name"
                ],
                "parentType": "Human",
                "returnType": "String",
                "fieldName": "name",
                "startOffset": 134875089,
                "duration": 6342
              }
            ]
          }
        }
      }
    }

Field Validation Instrumentation
--------------------------------

``graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation`` is an ``Instrumentation`` implementation that
can be used to validate fields and their arguments before query execution.  If errors are returned during this process then
the query execution is aborted and the errors will be in the query result.

You can make you own custom implementation of ``FieldValidation`` or you can use the ``SimpleFieldValidation`` class
to add simple per field checks rules.


.. code-block:: java

        ExecutionPath fieldPath = ExecutionPath.parse("/user");
        FieldValidation fieldValidation = new SimpleFieldValidation()
                .addRule(fieldPath, new BiFunction<FieldAndArguments, FieldValidationEnvironment, Optional<GraphQLError>>() {
                    @Override
                    public Optional<GraphQLError> apply(FieldAndArguments fieldAndArguments, FieldValidationEnvironment environment) {
                        String nameArg = fieldAndArguments.getFieldArgument("name");
                        if (nameArg.length() > 255) {
                            return Optional.of(environment.mkError("Invalid user name", fieldAndArguments));
                        }
                        return Optional.empty();
                    }
                });

        FieldValidationInstrumentation instrumentation = new FieldValidationInstrumentation(
                fieldValidation
        );

        GraphQL.newGraphQL(schema)
                .instrumentation(instrumentation)
                .build();

