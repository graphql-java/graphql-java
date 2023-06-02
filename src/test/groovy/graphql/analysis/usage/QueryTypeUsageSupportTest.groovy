package graphql.analysis.usage

import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class QueryTypeUsageSupportTest extends Specification {

    def sdl = """
        type Query {
            companies(type : CompanyType) : [Company]
        }
        
        enum CompanyType {
            GREEDY
        }
        
        interface Company implements NamedEntity {
            name : String
            parentCompany : Company
        }
        
        
        interface NamedEntity {
            name : String
        }
        
        
        type GlobalCompany implements Company & NamedEntity {
            name : String
            parentCompany : Company
        }

        type SmallBusiness implements Company & NamedEntity {
            name : String
            parentCompany : Company
        }
    """

    def schema = TestUtil.schema(sdl)

    def "can capture fields used"() {

        def query = """
            query q {
                companies(type : GREEDY) {                 
                  name
                }
            }
        """

        def document = Parser.parse(query)

        when:
        def usage = QueryTypeUsageSupport.getQueryTypeUsage(
                schema, document, null, CoercedVariables.emptyVariables()
        )

        then:
        usage != null

    }
}
