# extended-dependency-plugin [![Maven Central](https://img.shields.io/maven-central/v/com.github.vincentrussell/extended-dependency-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.vincentrussell%22%20AND%20a:%22extended-dependency-plugin%22) [![Build Status](https://travis-ci.org/vincentrussell/extended-dependency-plugin.svg?branch=master)](https://travis-ci.org/vincentrussell/extended-dependency-plugin)

extended-dependency-plugin will allow you to pull down all the dependency sources or javadoc jars for all the transitive
dependencies.

## Maven

Add a dependency to `com.github.vincentrussell:extended-dependency-plugin`.

```
<dependency>
   <groupId>com.github.vincentrussell</groupId>
   <artifactId>extended-dependency-plugin</artifactId>
   <version>1.0</version>
</dependency>
```

## Requirements
- JDK 1.8 or higher

## Running from the command line

get all sources transitively
```
mvn com.github.vincentrussell:extended-dependency-plugin:1.0:get -Dmaven.repo.local=/tmp/localRepo -Dartifact=org.elasticsearch.client:transport:6.7.1:jar:sources -Dtransitive=true
```

get all javadoc transitively
```
mvn com.github.vincentrussell:extended-dependency-plugin:1.0:get -Dmaven.repo.local=/tmp/localRepo -Dartifact=org.elasticsearch.client:transport:6.7.1:jar:javadoc -Dtransitive=true
```

# Change Log

## [1.0](https://github.com/vincentrussell/extended-dependency-plugin/tree/extended-dependency-plugin-1.0) (2021-01-15)

**Enhancements:**

- Initial Release

