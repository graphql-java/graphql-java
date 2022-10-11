package graphql.schema.diffing.ana;

import graphql.ExperimentalApi;

@ExperimentalApi
public interface SchemaChange {
    interface ObjectChange extends SchemaChange {

    }
    interface InterfaceChange extends SchemaChange {

    }
    interface UnionChange extends SchemaChange {

    }
    interface EnumChange extends SchemaChange {

    }
    interface InputObjectChange extends SchemaChange {

    }
    interface ScalarChange extends SchemaChange {

    }

}
