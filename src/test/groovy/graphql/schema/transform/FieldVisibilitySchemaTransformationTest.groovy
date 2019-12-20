package graphql.schema.transform

import graphql.TestUtil
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class FieldVisibilitySchemaTransformationTest extends Specification {

    def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
        return environment.fieldDefinition.directives.find({ directive -> directive.name == "private" }) == null
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

    def "leaves interface types referenced only by concrete classes"() {
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

    def "leaves interface type if has restricted and non-restricted reference"() {

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

    def "leaves concrete type if has restricted and non-restricted"() {
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

    def "removes interface type if only restricted reference with multiple interfaces"() {
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

}
