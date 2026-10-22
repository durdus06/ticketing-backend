# 1. AŞAMA: Java 25 ile uygulamayı derler.
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /app

# Maven Wrapper ve proje dosyalarını kopyalar.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Maven Wrapper'ı çalıştırılabilir yapar.
RUN chmod +x mvnw

# Maven bağımlılıklarını önceden indirerek build cache kullanır.
RUN ./mvnw -B dependency:go-offline

# Uygulama kaynak kodlarını kopyalar.
COPY src/ src/

# Spring Boot uygulamasını paketler.
# Testler CI aşamasında ayrıca çalıştırılacak.
RUN ./mvnw -B package -DskipTests


# 2. AŞAMA: Uygulamayı daha küçük bir Java runtime ile çalıştırır.
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# Uygulamayı root olmayan kullanıcıyla çalıştırır.
RUN groupadd --system spring && \
    useradd --system --gid spring spring

# Builder aşamasında üretilen JAR dosyasını kopyalar.
COPY --from=builder --chown=spring:spring \
    /app/target/*.jar app.jar

USER spring

# Spring Boot'un varsayılan HTTP portunu belgeler.
EXPOSE 8080

# JVM'in container bellek sınırını dikkate alarak çalışmasını sağlar.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]