Contributions
=============

Every contribution to make this project better is welcome: Thank you!

In order to make this a pleasant as possible for everybody involved, here are some tips:

* Respect the [Code of Conduct](#code-of-conduct)

* Before opening an Issue to report a bug, please try the latest development version. It can happen that the problem is already solved.

* Please use  Markdown to format your comments properly. If you are not familiar with that: `Getting started with writing and formatting on GitHub <https://help.github.com/articles/getting-started-with-writing-and-formatting-on-github/>`_

* For Pull Requests:
  * Here are some `general tips <https://github.com/blog/1943-how-to-write-the-perfect-pull-request>`_

  * Please be a as focused and clear as possible  and don't mix concerns. This includes refactorings mixed with bug-fixes/features, see [Open Source Contribution Etiquette](http://tirania.org/blog/archive/2010/Dec-31.html)

  * It would be good to add a automatic test. All tests are written in `Spock <http://spockframework.github.io/spock/docs/1.0/index.html>`_.


Build and test locally
----------------------

Just clone the repo and type

.. code-block:: sh

    ./gradlew build

In ``build/libs`` you will find the jar file.

Running the tests:

.. code-block:: sh

    ./gradlew test

Installing in the local Maven repository:

.. code-block:: sh

    ./gradlew install
