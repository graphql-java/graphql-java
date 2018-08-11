Fetching data
=============

How graphql fetches data
------------------------

Each field in graphql has a ``graphql.schema.DataFetcher`` associated with it.

Some fields will use specialised data fetcher code that knows how to go to a database say to get field information while
most simply take data from the returned in memory objects using the field name and Plain Old Java Object (POJO) patterns
to get the data.

    `Note : Data fetchers are some times called "resolvers" in other graphql implementations.`

So imagine a type declaration like the one below :


.. code-block:: graphql

    type Query {
        products(match : String) : [Product]   # a list of products
    }

    type Product {
        id : ID
        name : String
        description : String
        cost : Float
        tax : Float
        launchDate(dateFormat : String = "dd, MMM, yyyy') : String
    }

The ``Query.products`` field ihas a data fetcher, as does each field in the type ``Product``.

The data fetcher on the ``Query.products`` field is likely to be a more complex data fetcher, containing code that
goes to a database say to get a list of ``Product`` objects.  It takes an optional ``match`` argument and hence can filter these
product results if the client specified it.

It might look like the following :

.. code-block:: java

        DataFetcher productsDataFetcher = new DataFetcher<List<ProductDTO>>() {
            @Override
            public List<ProductDTO> get(DataFetchingEnvironment environment) {
                DatabaseSecurityCtx ctx = environment.getContext();

                List<ProductDTO> products;
                String match = environment.getArgument("match");
                if (match != null) {
                    products = fetchProductsFromDatabaseWithMatching(ctx, match);
                } else {
                    products = fetchAllProductsFromDatabase(ctx);
                }
                return products;
            }
        };

Each ``DataFetcher`` is passed a ``graphql.schema.DataFetchingEnvironment`` object which contains what field is being fetched, what
arguments have been supplied to the field and other information such as the field's type, its parent type, the query root object or the query
context object.

Note how the data fetcher code above uses the ``context`` object as an application specific security handle to get access
to the database.  This is a common technique to provide lower layer calling context.

Once we have a list of ``ProductDTO`` objects we typically don't need specialised data fetchers on each field.  graphql-java
ships with a smart ``graphql.schema.PropertyDataFetcher`` that knows how to follow POJO patterns based
on the field name.  In the example above there is a ``name`` field and hence it will try to look for a ``public String getName()``
POJO method to get the data.


``graphql.schema.PropertyDataFetcher`` is the data fetcher that is automatically associated with each field by default.

You can however still get access to the ``graphql.schema.DataFetchingEnvironment`` in your DTO methods.  This allows you to
tweak values before sending them out.  For example above we have a ``launchDate`` field that takes an optional ``dateFormat``
argument.  We can have the ProductDTO have logic that applies this date formatting to the desired format.


.. code-block:: java

    class ProductDTO {

        private ID id;
        private String name;
        private String description;
        private Double cost;
        private Double tax;
        private LocalDateTime launchDate;

        // ...

        public String getName() {
            return name;
        }

        // ...

        public String getLaunchDate(DataFetchingEnvironment environment) {
            String dateFormat = environment.getArgument("dateFormat");
            return yodaTimeFormatter(launchDate,dateFormat);
        }
    }

Customising PropertyDataFetcher
-------------------------------

As mentioned above ``graphql.schema.PropertyDataFetcher`` is the default data fetcher for fields in graphql-java and it will use standard patterns for fetching
object field values.

It supports a ``POJO`` approach and a ``Map`` approach in a Java idiomatic way.  By default it assumes that for a graphql field ``fieldX`` it can find a POJO property
called ``fieldX`` or a map key called ``fieldX`` if the backing object is a ``Map``.

However you may have small differences between your graphql schema naming and runtime object naming.  For example imagine that ``Product.description`` is actually
represented as ``getDesc()`` in the runtime backing Java object.

If you are using SDL to specify your schema then you can use the ``@fetch`` directive to indicate this remapping.

.. code-block:: graphql

    directive @fetch(from : String!) on FIELD_DEFINITION

    type Product {
        id : ID
        name : String
        description : String @fetch(from:"desc")
        cost : Float
        tax : Float
    }

This will tell the ``graphql.schema.PropertyDataFetcher`` to use the property name ``desc`` when fetching data for the graphql field named ``description``.

If you are hand coding your schema then you can just specify it directly by wiring in a field data fetcher.

.. code-block:: java

        GraphQLFieldDefinition descriptionField = GraphQLFieldDefinition.newFieldDefinition()
                .name("description")
                .type(Scalars.GraphQLString)
                .dataFetcher(PropertyDataFetcher.fetching("desc"))
                .build();



The interesting parts of the DataFetchingEnvironment
----------------------------------------------------

Every data fetcher is passed a ``graphql.schema.DataFetchingEnvironment`` object which allows it to know more about what is being fetched
and what arguments have been provided.  Here are some of the more interesting parts of ``DataFetchingEnvironment``.

* ``<T> T getSource()`` - the ``source`` object is used to get information for a field.  In the simple case its the in memory
TDO and hence simple POJO getters will be used for fields values.  In more complex cases, you may examine it to know
how to get the specific information for the current field.  As the graphql field tree is executed, each returned field value
becomes the ``source`` object for child fields.

* ``<T> T getRoot()`` - this special object is used to seed the graphql query.  The ``root`` and the ``source`` is the same thing for the
top level fields.  The root object never changes during the query and it may be null and hence no used.

* ``Map<String, Object> getArguments()`` - this represents the arguments that have been provided on a field and the values of those
arguments that have been resolved from passed in variables, AST literals and default argument values.  You use the arguments
of a field to control what values it returns.

* ``<T> T getContext()`` - the context is object is set up when the query is first executed and stays the same over the lifetime
of the query.  The context can be any value and is typically used to give each data fetcher some calling context needed
when trying to get field data.  For example the current user credentials or the database connection parameters could be contained
with a ``context`` object so that data fetchers can make business layer calls.  One of the key design decisions you have as a graphql
system designer is how you will use context in your fetchers if at all.  Some people use a dependency framework that injects context into
data fetchers automatically and hence don't need to use this.


* ``ExecutionTypeInfo getFieldTypeInfo()`` - the field type information is a catch all bucket of field type information that is built up as
the query is executed.  The following section explains more on this.

* ``DataFetchingFieldSelectionSet getSelectionSet()`` - the selection set represents the child fields that have been "selected" under neath the
currently executing field. This can be useful to help look ahead to see what sub field information a client wants.  The following section explains more on this.

* ```ExecutionId getExecutionId()`` - each query execution is given a unique id.  You can use this perhaps on logs to tag each individual
query.




The interesting parts of ExecutionTypeInfo
------------------------------------------

The execution of a graphql query creates a call tree of fields and their types.  ``graphql.execution.ExecutionTypeInfo.getParentTypeInfo``
allows you to navigate upwards and see what types and fields led to the current field execution.

Since this forms a tree path during execution, the ``graphql.execution.ExecutionTypeInfo.getPath`` method returns the representation of that
path.  This can be useful for logging and debugging queries.

There are also helper methods there to help you get the underlying type name of non null and list wrapped types.


The interesting parts of DataFetchingFieldSelectionSet
------------------------------------------------------

Imagine a query such as the following


.. code-block:: graphql

    query {
        products {
            # the fields below represent the selection set
            name
            description
            sellingLocations {
                state
            }
        }
    }


The sub fields here of the ``products`` field represent the selection set of that field.  It can be useful to know what sub selection has been asked for
so the data fetcher can optimise the data access queries.  For example an SQL backed system may be able to use the field sub selection to
only retrieve the columns that have been asked for.

In the example above we have asked for ``selectionLocations`` information and hence we may be able to make an more efficient data access query where
we ask for product information and selling location information at the same time.

