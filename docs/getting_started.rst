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


Using the latest development build
----------------------------------

The latest development build is also available on maven-central.

Please look at `Latest Build <https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion>`_ for the
latest version value.


How to use the latest build with Gradle
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Make sure ``mavenCentral`` is among your repos:

.. code-block:: groovy

    repositories {
        mavenCentral()
    }


Dependency:

.. code-block:: groovy

    dependencies {
      compile 'com.graphql-java:graphql-java:INSERT_LATEST_VERSION_HERE'
    }



How to use the latest build with Maven
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


Dependency:

.. code-block:: xml

    <dependency>
        <groupId>com.graphql-java</groupId>
        <artifactId>graphql-java</artifactId>
        <version>INSERT_LATEST_VERSION_HERE</version>
    </dependency>



