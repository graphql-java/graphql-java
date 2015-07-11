package graphql

import graphql.schema.DataFetcher


class StarWarsData {


    static def luke = [
            id        : '1000',
            name      : 'Luke Skywalker',
            friends   : ['1002', '1003', '2000', '2001'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Tatooine'
    ];

    static def vader = [
            id        : '1001',
            name      : 'Darth Vader',
            friends   : ['1004'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Tatooine',
    ];

    static def han = [
            id       : '1002',
            name     : 'Han Solo',
            friends  : ['1000', '1003', '2001'],
            appearsIn: [4, 5, 6],
    ];

    static def leia = [
            id        : '1003',
            name      : 'Leia Organa',
            friends   : ['1000', '1002', '2000', '2001'],
            appearsIn : [4, 5, 6],
            homePlanet: 'Alderaan',
    ];

    static def tarkin = [
            id       : '1004',
            name     : 'Wilhuff Tarkin',
            friends  : ['1001'],
            appearsIn: [4],
    ];

    static def humanData = [
            1000: luke,
            1001: vader,
            1002: han,
            1003: leia,
            1004: tarkin,
    ];

    static def threepio = [
            id             : '2000',
            name           : 'C-3PO',
            friends        : ['1000', '1002', '1003', '2001'],
            appearsIn      : [4, 5, 6],
            primaryFunction: 'Protocol',
    ];

    static def artoo = [
            id             : '2001',
            name           : 'R2-D2',
            friends        : ['1000', '1002', '1003'],
            appearsIn      : [4, 5, 6],
            primaryFunction: 'Astromech',
    ];

    static def droidData = [
            2000: threepio,
            2001: artoo,
    ]

    static def getCharacter(String id) {
        if (humanData[id] != null) return humanData[id]
        if (droidData[id] != null) return droidData[id]
        return null
    }

    static DataFetcher humanDataFetcher = new DataFetcher() {
        @Override
        Object get(Object source, List<Object> arguments) {
            def id = arguments[0]
            humanData[id]
        }
    }


    static DataFetcher droidDataFetcher = new DataFetcher() {
        @Override
        Object get(Object source, List<Object> arguments) {
            def id = arguments[0]
            droidData[id]
        }
    }

     static DataFetcher friendsDataFetcher = new DataFetcher() {
        @Override
        Object get(Object source, List<Object> arguments) {
            List<Object> result = []
            for (String id : source.friends) {
                return result.add(getCharacter(id))
            }
            result
        }
    }


}
