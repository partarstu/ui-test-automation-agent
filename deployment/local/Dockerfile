# Use the accetto VNC image as the base
# Using the 'latest' tag is convenient, but for production, you might want to pin to a specific version.
FROM accetto/ubuntu-vnc-xfce-chromium-g3:latest

ARG APP_DIR=/app
ARG APP_LOG_DIR=${APP_DIR}/log
ARG SOURCE_JAR_PATH=target/ui_test_java-1.0.0-SNAPSHOT.jar
ARG DESTINATION_JAR_PATH=${APP_DIR}/app.jar
ARG SOURCE_START_SCRIPT=deployment/java_app_startup.sh
ARG DESTINATION_START_SCRIPT=${APP_DIR}/java_app_startup.sh
ARG JAVA_VERSION=24

# Switch to root user to install software
USER 0

# --- Java Installation ---
# Install a Java Runtime Environment (JRE).
# The base image is Ubuntu, so we can use apt-get.
# '-y' answers yes to any prompts.
# '&&' chains commands together.
# 'apt-get clean' removes downloaded package files to keep the image small.
RUN apt-get update
RUN wget https://download.oracle.com/java/${JAVA_VERSION}/latest/jdk-${JAVA_VERSION}_linux-x64_bin.deb
RUN dpkg -i jdk-${JAVA_VERSION}_linux-x64_bin.deb
RUN java --version
RUN update-alternatives --list java
RUN apt-get install --reinstall libgtk2.0-0 -y
RUN apt-get clean

# --- Application Setup ---
# Copy your Java application JAR file into the container.
# We'll place it in the app directory.
COPY ${SOURCE_JAR_PATH} ${DESTINATION_JAR_PATH}
RUN mkdir -p ${APP_LOG_DIR}
RUN chmod 777 -R ${APP_LOG_DIR}

# This script will be executed after the main VNC and desktop services are running.
COPY ${SOURCE_START_SCRIPT} ${DESTINATION_START_SCRIPT}
RUN chmod 777 ${DESTINATION_START_SCRIPT}

# --- Environment Variables (Optional) ---
ENV APP_JAR_PATH=${DESTINATION_JAR_PATH}
ENV DISPLAY=:1
ENV GROQ_API_KEY=""
ENV GROQ_ENDPOINT="https://api.groq.com/openai/v1"
ENV INSTRUCTION_MODEL_NAME="meta-llama/llama-4-maverick-17b-128e-instruct"
ENV LOG_LEVEL="INFO"
ENV logging.level.dev.langchain4j="INFO"
ENV MODEL_PROVIDER="groq"
ENV UNATTENDED_MODE="false"
ENV VISION_MODEL_NAME="meta-llama/llama-4-maverick-17b-128e-instruct"
ENV VECTOR_DB_URL=""
ENV SCREENSHOTS_SAVE_FOLDER="${APP_DIR}/screenshots"
ENV LOG_DIR=${APP_LOG_DIR}
ENV DEBUG_MODE="true"
ENV DEPLOYMENT_ENV="local"