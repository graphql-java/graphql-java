Thanks for contributing to graphql-java!


Please be sure that you read the [Code of Conduct](CODE_OF_CONDUCT.md) before contributing to this project
and please create a new Issue and discuss first what your are planing todo for bigger changes.


The overall goal of graphql-java is to have a correct implementation of the [GraphQL Spec](https://github.com/facebook/graphql/) in a production ready way.

In order to achieve that we have a strong focus on maintainability and high test coverage:

- We expect new or modified unit test for every change (written in [Spock](http://spockframework.org/)).

- Your code should should be formatted with our IntelliJ [graphql-java-code-style](graphql-java-code-style.xml). 

- We don't add a new dependency to graphql-java: dependency conflicts will make adaption of graphql-java harder for users, 
therefore we avoid adding any new dependency.

- graphql-java has a strict focus on executing a GraphQL request, this means JSON parsing, http communication, databases
access etc is out of scope.


If you have any question please open a Issue.

Thanks! 
  

