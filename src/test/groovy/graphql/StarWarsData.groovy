package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.EnumValuesProvider

class StarWarsData {


    static def luke = [
            id        : '1000',
            name      : 'Luke Skywalker',
            friends   : ['1002', '1003', '2000', '2001'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Tatooine'
    ]

    static def vader = [
            id        : '1001',
            name      : 'Darth Vader',
            friends   : ['1004'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Tatooine',
    ]

    static def han = [
            id       : '1002',
            name     : 'Han Solo',
            friends  : ['1000', '1003', '2001'],
            appearsIn: [4, 5, 6],
    ]

    static def leia = [
            id        : '1003',
            name      : 'Leia Organa',
            friends   : ['1000', '1002', '2000', '2001'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Alderaan',
    ]

    static def tarkin = [
            id       : '1004',
            name     : 'Wilhuff Tarkin',
            friends  : ['1001'],
            appearsIn: [4],
    ]

    static def humanData = [
            '1000': luke,
            '1001': vader,
            '1002': han,
            '1003': leia,
            '1004': tarkin,
    ]

    static def threepio = [
            id             : '2000',
            name           : 'C-3PO',
            friends        : ['1000', '1002', '1003', '2001'],
            appearsIn      : [4, 5, 6],
            primaryFunction: 'Protocol',
    ]

    static def artoo = [
            id             : '2001',
            name           : 'R2-D2',
            friends        : ['1000', '1002', '1003'],
            appearsIn      : [4, 5, 6],
            primaryFunction: 'Astromech',
    ]

    static def droidData = [
            "2000": threepio,
            "2001": artoo,
    ]

    static boolean isHuman(String id) {
        return humanData[id] != null
    }

    static def getCharacter(String id) {
        if (humanData[id] != null) return humanData[id]
        if (droidData[id] != null) return droidData[id]
        return null
    }

    static DataFetcher humanDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            def id = environment.arguments.id
            humanData[id]
        }
    }


    static DataFetcher droidDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            def id = environment.arguments.id
            droidData[id]
        }
    }

    static TypeResolver characterTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            def id = env.getObject().id
            if (humanData[id] != null)
                return StarWarsSchema.humanType
            if (droidData[id] != null)
                return StarWarsSchema.droidType
            return null
        }
    }

    static DataFetcher friendsDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            List<Object> result = []
            for (String id : environment.source.friends) {
                result.add(getCharacter(id))
            }
            result
        }
    }

    static DataFetcher heroDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            if (environment.containsArgument("episode")
                    && 5 == environment.getArgument("episode")) return luke
            return artoo
        }
    }

    static EnumValuesProvider episodeResolver = new EnumValuesProvider() {
        @Override
        Object getValue(String name) {
            switch (name) {
                case "NEWHOPE": return 4
                case "EMPIRE": return 5
                case "JEDI": return 6
            }
            null
        }
    }
}
