/**
 * The purpose of this code is to show an example of serving a graphql query over HTTP
 *
 * More info can be found here : http://graphql.org/learn/serving-over-http/
 *
 * There are more concerns in a fully fledged application such as your approach to permissions
 * and authentication and so on that are not shown here.
 *
 * The backing data is the "star wars" example schema.  And fairly complex example query is as follows :
 *
 * <pre>
 * {@code
 * {
 *      luke: human(id: "1000") {
 *          ...HumanFragment
 *      }
 *      leia: human(id: "1003") {
 *          ...HumanFragment
 *      }
 *  }
 *
 *  fragment HumanFragment on Human {
 *      name
 *      homePlanet
 *      friends {
 *      name
 *      __typename
 *  }
 * }
 *
 * }
 * </pre>{
 */
package example.http;