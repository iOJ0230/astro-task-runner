# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY . .

# Make the Gradle wrapper executable (Windows → Linux)
RUN chmod +x gradlew

# Build a fat jar using Gradle wrapper
RUN ./gradlew clean shadowJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]