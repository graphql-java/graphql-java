Field Selection
===============

Field selection occurs when you have a compound type (an object or interface type) and you select a set of sub fields
from that type.

For example given the following query :

.. code-block:: graphql

    query {
        user(userId : "xyz")  {
            name
            age
            weight
            friends {
                name
            }
        }
    }

The field selection set of the ``user`` field is ``name``, ``age``, ``weight``, ``friends`` and ``friends/name``

Knowing the field selection set can help make ``DataFetcher``s more efficient.  For example in the above query
imagine that the ``user`` field is backed by an SQL database system.  The data fetcher could look ahead into the field selection
set and use different queries because it knows the caller wants friend information as well as user information.

``graphql.schema.DataFetchingFieldSelectionSet`` is used to represent this field selection set.  It gives you maps
of the fields and their ``graphql.schema.GraphQLFieldDefinition``s and argument values.


.. code-block:: java

        DataFetcher smartUserDF = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment env) {
                String userId = env.getArgument("userId");

                DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();
                if (selectionSet.contains("user/*")) {
                    return getUserAndTheirFriends(userId);
                } else {
                    return getUser(userId);
                }
            }
        };

A glob path matching system is used for addressing fields in the selection.  Its based on ``java.nio.file.FileSystem#getPathMatcher``
as an implementation.

This will allow you to use ``*``, ``**`` and ``?`` as special matching characters such that ``invoice/customer*`` would
match an ``invoice`` field with child fields that start with ``customer``.  Each level of field is separated by ``/`` just like
a file system path.

There are methods that allow you to get more detailed information about the fields in the selection set.  For example
if you are using ``Relay`` (https://facebook.github.io/relay/docs/en/graphql-server-specification.html) often you want to know what fields have
been request in the ``Connection`` section of the query.

So given a query like:


.. code-block:: graphql

    query {
        users(first:10)  {
            edges {
                node {
                    name
                    age
                    weight
                    friends {
                        name
                    }
                }
            }
        }
    }

you can write code that gets the details of each specific field that matches a glob.


.. code-block:: java

        DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();
        List<SelectedField> nodeFields = selectionSet.getFields("edges/nodes/*");
        nodeFields.forEach(selectedField -> {
            System.out.println(selectedField.getName());
            System.out.println(selectedField.getFieldDefinition().getType());

            DataFetchingFieldSelectionSet innerSelectionSet = selectedField.getSelectionSet();
            // this forms a tree of selection and you can get very fancy with it
        }


