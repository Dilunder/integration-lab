FROM eclipse-temurin:21-jdk AS build
WORKDIR /source
COPY DemoShop.java .
RUN javac DemoShop.java
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/*.class ./
USER 10001
ENTRYPOINT ["java","DemoShop"]
