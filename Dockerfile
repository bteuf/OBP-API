
FROM maven:3-jdk-11 as maven
# Build the source using maven, source is copied from the 'repo' build.
ADD . /usr/src/OBP-API
RUN cp /usr/src/OBP-API/obp-api/pom.xml /tmp/pom.xml # For Packaging a local repository within the image
WORKDIR /usr/src/OBP-API
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props
RUN mvn install -pl .,obp-commons
RUN mvn install -DskipTests -pl obp-api

FROM jetty:9.4-jre11-slim

# Copy OBP source code
# Copy build artifact (.war file) into jetty from 'maven' stage.
COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api-*.war /var/lib/jetty/webapps/ROOT.war

