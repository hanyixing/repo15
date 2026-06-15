# syntax=docker/dockerfile:1
#
# Multi-stage build for the Kiss framework.
#  * Build stage uses the project's own ./bld build (NOT Maven) to produce
#    work/Kiss.war.  Dependency downloads happen inside the Java builder, so
#    only a JDK + network are required here.
#  * Runtime stage is Tomcat 11 on JDK 17 with the app exploded into ROOT.
#
# No secrets are baked into any layer; runtime configuration is applied by
# docker-entrypoint.sh from environment variables (see docker-compose.yml).

# ---- Build stage: produce work/Kiss.war ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
# Normalize line endings: a Windows (autocrlf) working tree gives bld CRLF,
# which makes the kernel try to exec `bash\r` from the shebang and fail.
# Stripping CR in-container keeps the build host-independent and does not
# modify the committed file (which is already LF in git).
RUN sed -i 's/\r$//' bld \
    && chmod +x bld \
    && ./bld war

# ---- Runtime stage: Tomcat 11 / JDK 17 ----
FROM tomcat:11.0-jdk17-temurin AS runtime

# Drop the stock ROOT app and explode our war in its place at BUILD time, so
# that WEB-INF/backend/application.ini exists on disk before the container
# starts and docker-entrypoint.sh can edit it prior to launching Tomcat.
RUN rm -rf "${CATALINA_HOME}/webapps/ROOT" "${CATALINA_HOME}/webapps/ROOT.war"
COPY --from=build /src/work/Kiss.war /tmp/Kiss.war
RUN mkdir -p "${CATALINA_HOME}/webapps/ROOT" \
    && (cd "${CATALINA_HOME}/webapps/ROOT" && jar -xf /tmp/Kiss.war) \
    && rm -f /tmp/Kiss.war

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
