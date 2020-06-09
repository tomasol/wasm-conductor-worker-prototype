FROM openjdk:14.0-buster
# install wasmer
RUN curl https://get.wasmer.io -sSfL | sh

# copy files
ARG JAR_FILE=build/libs/*.jar
ARG QUICKJS_FOLDER=src/main/resources/quickjs
ARG PYTHON_FOLDER=src/main/resources/python
COPY ${JAR_FILE} app.jar
COPY ${QUICKJS_FOLDER} src/main/resources/quickjs
COPY ${PYTHON_FOLDER} src/main/resources/python
ENTRYPOINT ["java","-Dpath.wasmer=/root/.wasmer/bin/wasmer","-jar","/app.jar"]
