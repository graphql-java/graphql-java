# GraphQL Java Coding guidelines used in GraphQL Java


## General principles

- We prefer closer to zero dependencies. Dont bring in guava, apache-commons, spring-xxx, y or z however much some StringUtils method might be useful. Less dependencies makes graphql-java more applicable to everyone

- We prefer staying out of the HTTP stack. We are the low level engine of running graphql queries. Other concerns such as JSON and HTTP are handled better by other layers

- We prefer simple code to clever code. It should be readable with well named methods. Clever nested streams and lambdas are not our thing.

- We prefer general to specific. So the code should be generally applicable to use cases, not highly specific to just some use cases, even if it takes more setup.


## more specific topics

- all tests are written in spock

- Optional vs Null: TBD how to move forward

- Non static methods/classes: the default are non static methods. Static class are fine for "small" methods (often inside util classes like `GraphQLTypeUtil`). The rule of thumb is: if you **never** want to mock it out, it is fine to be a static method call.

- Use @Public and @Internal to communicate what level of stability is supported. 

- Never make a class or method package protected or protected: make it public or private and use @Internal to communicate that the class can be changed without notice. The user can decide itself about the risk when they use internal things.