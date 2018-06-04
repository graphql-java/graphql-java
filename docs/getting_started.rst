Getting started
===============

``graphql-java`` requires at least Java 8.


How to use the latest release with Gradle
-----------------------------------------

Make sure ``mavenCentral`` is among your repos:

.. code-block:: groovy

    repositories {
        mavenCentral()
    }


Dependency:

.. code-block:: groovy

    dependencies {
      compile 'com.graphql-java:graphql-java:8.0'
    }


How to use the latest release with Maven
----------------------------------------

Dependency:

.. code-block:: xml

    <dependency>
        <groupId>com.graphql-java</groupId>
        <artifactId>graphql-java</artifactId>
        <version>8.0</version>
    </dependency>


Hello World
-----------

This is the famous "hello world" in ``graphql-java``:

.. literalinclude:: ../src/test/java/HelloWorld.java
    :language: java


Using a development build
-------------------------

Every successful build is published on maven-central with the version "<time>-<short git hash>".
For example "2018-06-04T11-42-58-352f0df".

Please look at `Latest Build <https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion>`_ for the
latest version value.


How to use a development build with Gradle
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Make sure ``mavenCentral`` is among your repos:

.. code-block:: groovy

    repositories {
        mavenCentral()
    }


Dependency:

.. code-block:: groovy

    dependencies {
      compile 'com.graphql-java:graphql-java:INSERT_VERSION_HERE'
    }



How to use a development build with Maven
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


Dependency:

.. code-block:: xml

    <dependency>
        <groupId>com.graphql-java</groupId>
        <artifactId>graphql-java</artifactId>
        <version>INSERT_VERSION_HERE</version>
    </dependency>



