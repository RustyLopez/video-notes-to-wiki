FROM amazoncorretto:21-alpine

WORKDIR /app

COPY . .

# should already be built. Else this gets expensive.
# RUN chmod +x mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

#CMD ["ls", "-la", "target"]
 # TODO why am I having to put full name in here? With version... which makes it problematic
ENTRYPOINT ["java", "-jar", "target/video-notes-to-wiki-0.0.1-SNAPSHOT.jar"]