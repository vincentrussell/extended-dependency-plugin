
release process:

mvn javadoc:javadoc
mvn versions:set -DnewVersion=1.0
git add .
git commit -m "preparing for release 1.0"
git tag extended-dependency-plugin-1.0
mvn clean deploy -P release
mvn versions:set -DnewVersion=1.1-SNAPSHOT
git add .
git commit -m "preparing for development version 1.1"
git push --tags
