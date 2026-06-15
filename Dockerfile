# ==============================================================================
# Multi-stage Dockerfile for Kiss Framework Application
# ==============================================================================
# Stage 1: Build the WAR using the Kiss bld system
# Stage 2: Deploy to Tomcat 11
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Builder
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS builder

LABEL maintainer="Kiss Framework"
LABEL stage="builder"

WORKDIR /build

# Copy the entire project source
COPY . .

# Build the application and produce the WAR
RUN chmod +x bld && ./bld build
RUN ./bld war

# ------------------------------------------------------------------------------
# Stage 2: Runtime
# ------------------------------------------------------------------------------
FROM tomcat:11-jre17-temurin

LABEL maintainer="Kiss Framework"
LABEL description="Kiss Framework application running on Tomcat 11 with JDK 17"
LABEL version="1.0"

# Remove default Tomcat webapps to reduce attack surface and startup noise
RUN rm -rf \
    ${CATALINA_HOME}/webapps/ROOT \
    ${CATALINA_HOME}/webapps/docs \
    ${CATALINA_HOME}/webapps/examples \
    ${CATALINA_HOME}/webapps/manager \
    ${CATALINA_HOME}/webapps/host-manager

# Create application config directory
RUN mkdir -p /app

# Copy the WAR from the builder stage
COPY --from=builder /build/work/app.war ${CATALINA_HOME}/webapps/ROOT.war

# Copy the default application.ini so the app has a baseline config
COPY --from=builder /build/src/main/backend/application.ini /app/application.ini

# Expose the Tomcat HTTP port
EXPOSE 8080

# JVM tuning defaults — override via docker-compose or docker run -e
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# Healthcheck: verify Tomcat is responding
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Start Tomcat
CMD ["catalina.sh", "run"]
