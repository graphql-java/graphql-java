package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.GraphQLSchema
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

class EditOperationAnalyzerTest extends Specification {

    def "test field changed"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello2: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.size() == 1
        (changes[0] as SchemaChanges.FieldChanged).name == "hello2"
        (changes[0] as SchemaChanges.FieldChanged).fieldsContainer == "Query"
    }


    List<SchemaChanges.SchemaChange> changes(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }
}
