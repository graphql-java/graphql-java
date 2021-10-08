package graphql.schema.transform

import graphql.Scalars
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.TypeResolver
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

class FieldVisibilitySchemaTransformationTest extends Specification {

    def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
        def directives = (environment.schemaElement as GraphQLDirectiveContainer).directives
        return directives.find({ directive -> directive.name == "private" }) == null
    })

    def "can remove a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
    }

    def "can remove a type associated with a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
        }
        
        type SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "removes concrete types referenced only by interface"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData @private
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "interface and its implementations that have both private and public reference is retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData @private
            billingStatus2: SuperSecretCustomerData 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("SuperSecretCustomerData") != null
        restrictedSchema.getType("BillingStatus") != null
    }


    def "types with both private and public references are retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            public: Foo
            private: Bar @private
        }
        
        type Foo {
            x: X
        }
        
        type Bar {
            x: X
            bar2: Bar2
        }
        
        type Bar2 {
            id: Int
        }
        
        type X {
            x2: X2
        }
        
        type X2 {
            id: Int 
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Bar2") == null
        restrictedSchema.getType("X") != null
        restrictedSchema.getType("Foo") != null
        restrictedSchema.getType("X2") != null
    }

    def "union types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            privateFooOrBar: FooOrBar @private
            privateBar: Bar @private
            privateFoo: Foo @private
            public: Foo
        }
        
        union FooOrBar = Foo | Bar
        
        type Foo {
            id: ID
        }
        
        type Bar {
            id: ID
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateFooOrBar") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateBar") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateFoo") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("public") != null
        restrictedSchema.getType("FooOrBar") == null
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Foo") != null
    }

    def "union type with reference by private interface removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            public: Bar
            private: Baz @private
        }
        
        type Bar {
            id: ID
        }
        
        interface Baz {
            fooOrBar: FooOrBar
        }
        
        type Bing implements Baz {
            fooOrBar: FooOrBar
        }
        union FooOrBar = Foo | Bar
        
        type Foo {
            id: ID
        }
        
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("private") == null
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") != null
        restrictedSchema.getType("Baz") == null
        restrictedSchema.getType("Bing") == null
        restrictedSchema.getType("FooOrBar") == null
    }


    def "leaves concrete types referenced only by interfaces"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") != null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }

    def "leaves interface types referenced only by concrete types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") != null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }


    def "removes interface types implemented by types used in a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "leaves interface type if has private and public reference"() {

        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
            accountStatus: AccountStatus 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        type AccountStatus implements SuperSecretCustomerData {
            accountNumber: String
            inGoodStanding: Boolean
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }

    def "leaves concrete type if has public and private"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
            publicBillingStatus: BillingStatus
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("publicBillingStatus") != null
        restrictedSchema.getType("BillingStatus") != null
    }

    def "removes interface type if only private reference with multiple interfaces"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account implements PublicView {
            name: String
            billingStatus: BillingStatus @private
            country: String
        }
        
        type BillingStatus implements SuperSecretCustomerData & Billable & PublicView {
            accountNumber: String
            cardLast4: Int
            country: String
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        interface Billable {
            cardLast4: Int
        }
        
        interface PublicView {
            country: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
        restrictedSchema.getType("Billable") == null
        restrictedSchema.getType("PublicView") != null
    }

    def "primitive types are retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        # only String and Boolean types have other references (in the introspection query)
        type Query {
            bar: String @private
            baz: Boolean @private
            placeholderField: Int
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("String") != null
        restrictedSchema.getType("Boolean") != null
    }

    def "root types with different names are supported"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        schema {
           query: FooQuery
        }

        type FooQuery {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
    }

    def "fields and types are removed from subscriptions and mutations"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        schema {
           query: Query
           mutation: Mutation
           subscription: Subscription
        }
        
        type Query {
            something: String
        }

        type Mutation {
            setFoo(foo: String): Foo @private
            placeholderField: Int
        }
        
        type Subscription {
            barAdded: Bar @private
            placeholderField: Int
        }
        
        type Foo {
            foo: String
        }
        
        type Bar {
            bar: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") == null
    }

    def "type with both private and public transitive references is retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            private: Foo # calls Baz privately
            public: Bar # calls Baz publicly
        }
        
        type Foo {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Bar {
            baz: Baz
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("baz") == null
        restrictedSchema.getType("Baz") != null
    }

    def "type with multiple private parent references is removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo
            bar: Bar
        }
        
        type Foo {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Bar {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("baz") == null
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("baz") == null
        restrictedSchema.getType("Baz") == null
    }

    def "type with multiple private grandparent references is removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo @private
            bar: Bar @private
            placeholderField: Int
        }
        
        type Foo {
            baz: Baz 
        }
        
        type Bar {
            baz: Baz
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Baz") == null
    }

    def "type with circular reference can be traversed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            foo2: Foo @private
            bar: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("foo2") == null
        restrictedSchema.getType("Foo") != null
    }

    def "input types can have private fields"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Mutation {
            setFoo(fooInput: FooInput): Foo 
        }
        
        type Foo {
            id: ID
        }
        
        input FooInput {
            foo: BarInput @private
            bar: String
        }
        
        input BarInput {
            id: ID
        }
               
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("FooInput") as GraphQLInputObjectType).getFieldDefinition("foo") == null
        restrictedSchema.getType("FooInput") != null
        restrictedSchema.getType("BarInput") == null
    }

    def "enum types can be removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Mutation {
            setFoo(fooInput: FooInput): Foo 
        }
        
        type Foo {
            id: ID
            fooEnum: FooEnum @private
        }
        
        input FooInput {
            foo: BarEnum @private
            bar: String
        }
        
        enum BarEnum {
            FOO
            BAR
            BAZ
        }
        
        enum FooEnum {
            BING
            BOO
        }
               
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("FooInput") as GraphQLInputObjectType).getFieldDefinition("foo") == null
        restrictedSchema.getType("FooInput") != null
        restrictedSchema.getType("BarEnum") == null
        restrictedSchema.getType("FooEnum") == null
    }

    def "unreferenced types can have fields removed, and the referenced types must be removed as well if they are not used"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is not used anywhere else, it should be removed"
        restrictedSchema.getType("Bing") == null
    }

    def "unreferenced types can have fields removed, and referenced type must not be removed if used elsewhere in the connected graph"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
            zinc: Bing
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is used in the connected graph, it MUST not be removed"
        restrictedSchema.getType("Bing") != null
    }

    def "unreferenced types can have fields removed, and referenced type must not be removed if used elsewhere"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            foo: Bing
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is used elsewhere, it SHOULD not be removed"
        restrictedSchema.getType("Bing") != null
    }

    def "use type references - private field declared with interface type removes both concrete and interface"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("SuperSecretCustomerData")).withDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .withInterface(typeRef("SuperSecretCustomerData"))
                .build()

        def secretData = newInterface()
                .name("SuperSecretCustomerData")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .typeResolver(Mock(TypeResolver))
                .build()

        def schema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .additionalType(billingStatus)
                .additionalType(secretData)
                .additionalDirective(privateDirective)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }


    def "use type references - private field declared with concrete type removes both concrete and interface"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("BillingStatus")).withDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .withInterface(typeRef("SuperSecretCustomerData"))
                .build()

        def secretData = newInterface()
                .name("SuperSecretCustomerData")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .typeResolver(Mock(TypeResolver))
                .build()

        def schema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .additionalType(billingStatus)
                .additionalType(secretData)
                .additionalDirective(privateDirective)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "use type references - unreferenced types are removed"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("BillingStatus")).withDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .build()

        def schema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .additionalDirective(privateDirective)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
    }

    def "before and after transformation hooks are run"() {

        given:
        def callbacks = []

        def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
            def directives = (environment.schemaElement as GraphQLDirectiveContainer).directives
            return directives.find({ directive -> directive.name == "private" }) == null
        }, { -> callbacks << "before" }, { -> callbacks << "after"} )

        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        visibilitySchemaTransformation.apply(schema)

        then:
        callbacks.containsAll(["before", "after"])
    }
}
