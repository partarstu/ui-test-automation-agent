# AI-Powered UI Test Automation Agent

This project is a Java-based agent that leverages Generative AI models and Retrieval-Augmented Generation (RAG) to execute automated test
cases at the graphical user interface (GUI) level. It understands explicit natural language test case instructions (both actions and
verifications), performs corresponding actions using the mouse and keyboard, locates the required UI elements on the screen (if needed), and
verifies whether actual results correspond to the expected ones using computer vision capabilities.

[![Package Project](https://github.com/partarstu/ui-test-automation-agent/actions/workflows/package.yml/badge.svg)](https://github.com/partarstu/ui-test-automation-agent/actions/workflows/package.yml)

Here the corresponding article on
Medium: [AI Agent That’s Rethinking UI Test Automation](https://medium.com/@partarstu/meet-the-ai-agent-thats-rethinking-ui-test-automation-d8ef9742c6d5)

## Key Features

* **AI Model Integration:**
    * Utilizes the [LangChain4j](https://github.com/langchain4j/langchain4j) library to seamlessly interact with various Generative AI
      models.
    * Supports models from Google (via AI Studio or Vertex AI), Azure OpenAI, and Groq. Configuration is managed through `config.properties`
      and
      `AgentConfig.java`, allowing specification of providers, model names (`instruction.model.name`, `vision.model.name`), API keys/tokens,
      endpoints, and generation parameters (temperature, topP, max output tokens, retries).
    * Leverages separate models for instruction understanding (test case actions and verifications) and vision-based tasks like locating the
      best matching UI element, suggesting a new UI element's description, and verifying actual vs. expected results.
    * Uses structured prompts to guide model responses and ensure output can be parsed into required DTOs.
    * Includes options for model logging (`model.logging.enabled`) and outputting the model's thinking process (`thinking.output.enabled`).

* **RAG:**
    * Employs a Retrieval-Augmented Generation (RAG) approach to manage information about UI elements.
    * Uses a vector database to store and retrieve UI element details (name, element description, anchor element descriptions, page summary,
      and
      screenshot). It currently supports only Chroma DB (`AgentConfig.getVectorDbProvider` -> `chroma`), configured via `vector.db.url` in
      `config.properties`.
    * Stores UI element information as `UiElement` records, which include a name, self-description, description of surrounding
      elements (anchors), a page summary, and a screenshot (`UiElement.Screenshot`).
    * Retrieves the top N (`retriever.top.n` in config) most relevant UI elements based on semantic similarity between the query (derived
      from the test step action) and based on the stored element names. Minimum similarity scores (`element.retrieval.min.target.score`,
      `element.retrieval.min.general.score`, `element.retrieval.min.page.relevance.score` in config) are used to filter results for target
      element identification and potential refinement
      suggestions.

* **Computer Vision:**
    * Employs a hybrid approach combining large vision models with traditional computer vision algorithms (OpenCV's ORB and Template
      Matching) for robust UI element location.
    * Leverages a vision-capable AI model (`ModelFactory.getVisionModel`) to:
        * Identify potential bounding boxes for UI elements on the screen.
        * Disambiguate when multiple visual matches are found or to confirm that a single visual match, if found, corresponds to the target
          element's description and surrounding element information.
    * Uses OpenCV (via `org.bytedeco.opencv`) for visual pattern matching (ORB and Template Matching) to find occurrences of an element's
      stored screenshot on the current screen.
    * Intelligent logic in `ElementLocator` combines results from the vision model and algorithmic matching, considering intersections and
      relevance, to determine the best match.

* **GUI Interaction Tools:**
    * Provides a set of [tools](src/main/java/org/tarik/ta/tools) for interacting with the GUI using Java's `Robot` class.
    * [MouseTools](src/main/java/org/tarik/ta/tools/MouseTools.java) offer actions for working with the mouse (clicks, hover,
      click-and-drag, etc.). These tools typically locate the target UI element first,
      using [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java).
    * [KeyboardTools](src/main/java/org/tarik/ta/tools/KeyboardTools.java) provide actions for working with the keyboard (typing text into
      specific elements,
      clearing data from input fields, pressing single keys or key combinations, etc.).
    * [CommonTools](src/main/java/org/tarik/ta/tools/CommonTools.java) include common actions like waiting for a specified duration and
      opening the Chrome browser.

* **Attended and Unattended Modes:**
    * Supports two execution modes controlled by the `unattended.mode` flag in `config.properties`.
    * **Attended ("Trainee") Mode (`unattended.mode=false`):** Designed for initial test case runs or when execution in unattended mode
      fails for debugging/fixing purposes. In this mode the agent behaves as a trainee, who needs assistance from the human tutor/mentor
      in order to identify all the information which is required for the unattended (without supervision) execution of the test case.
    * **Unattended Mode (`unattended.mode=true`):** The agent executes the test case without any human assistance. It relies entirely on the
      information stored in the RAG database and the AI models' ability to interpret instructions and locate elements based on stored data.
      Errors during element location or verification will cause the execution to fail. This mode is suitable for integration into CI/CD
      pipelines.

* **Flexible Execution:**
    * The agent can be executed in two primary ways:
        * **CLI Mode:** This mode allows executing the agent directly on the local machine, usually in attended mode.
          The [Agent](src/main/java/org/tarik/ta/Agent.java) class is the entry point. It accepts the path to a test case JSON file (like
          [this one](src/test/resources/use_case.json)) as a command-line argument and executes the test case.
        * **Server Mode (A2A Agent):** This mode allows the agent to be accessed as part of an agent "swarm" executing test cases in a
          distributed
          manner. The [Server](src/main/java/org/tarik/ta/Server.java) class is the entry point where a Javalin web server is started.
          The agent registers its capabilities and listens for A2A JSON-RPC requests on the root endpoint (`/`) (port configured via `port`
          in `config.properties`).
          The server accepts only one test case execution at a time (the agent has been designed as a static utility for simplicity
          purposes).
          Upon receiving a valid request when idle, it returns `200 OK` and starts the test case execution. If busy, it returns
          `429 Too Many Requests`.

## Test Case Execution Workflow

The test execution process, orchestrated by the `Agent` class, follows these steps:

1. **Test Case Processing:** The agent loads the test case defined in a JSON file (e.g., [this one](src/test/resources/use_case.json)).
   This file contains the overall test case name, optional `preconditions` (natural language description of the required state before
   execution), and a list of `TestStep`s. Each `TestStep` includes a `stepDescription` (natural language
   instruction), optional `testData` (inputs for the step), and `expectedResults` (natural language description of the expected state after
   the step).
2. **Precondition Verification:** If preconditions are defined, the agent verifies them against the current UI state using a vision model.
   If preconditions are not met, the test case execution fails.
3. **Test Case Execution Plan Generation:** The agent generates a `TestCaseExecutionPlan` using an instruction model, outlining the specific
   tool calls and arguments for each `TestStep`.
4. **Step Iteration:** The agent iterates through each `TestStep` sequentially, executing the planned tool calls.
5. **Action Processing (for each Action step):**
    * **Tool Execution:** The appropriate tool method with the arguments provided by the execution plan is invoked.
    * **Element Location (if required by the tool):** If the requested tool needs to interact with a specific UI element (e.g., clicking an
      element), the element is located using the [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java) class based on the
      element's description (provided as a parameter for the tool). (See "UI Element Location Workflow" below for details).
    * **Retry/Rerun Logic:** If a tool execution reports that retrying makes sense (e.g., an element was not found on the screen), the
      agent retries the execution after a short delay, up to a configured timeout (`test.step.execution.retry.timeout.millis`). If the
      error persists after the deadline, the test case execution is marked as `ERROR`.
6. **Verification Processing (for each Verification step):**
    * **Delay:** A short delay (`action.verification.delay.millis`) is introduced to allow the UI state to change after the preceding
      action.
    * **Screenshot:** A screenshot of the current screen is taken.
    * **Vision Model Interaction:** A verification prompt containing the expected results description and the current screenshot is sent to
      the configured vision AI model. The model analyzes the screenshot and compares it against the expected results description.
    * **Result Parsing:** The model's response contains information indicating whether the verification passed, and extended information
      with the justification for the result.
    * **Retry Logic:** If the verification fails, the agent retries the verification process after a short interval (
      `test.step.execution.retry.interval.millis`) until a timeout (`verification.retry.timeout.millis`) is reached. If it still fails after
      the deadline, the test case execution is marked as `FAILED`.
7. **Completion/Termination:** Execution continues until all steps are processed successfully or an interruption (error, verification
   failure, user termination) occurs. The final `TestExecutionResult` (including `TestExecutionStatus` and detailed `TestStepResult` for
   each step) is returned.

### UI Element Location Workflow

The [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java) class is responsible for finding the coordinates of a target UI
element based on its natural language description provided by the instruction model during an action step. This involves a combination of
RAG, computer vision, analysis, and potentially user interaction (if run in attended mode):

1. **RAG Retrieval:** The provided UI element's description is used to query the vector database, where the top N (`retriever.top.n`) most
   semantically similar `UiElement` records are retrieved based on their stored names, using embeddings generated by
   the `all-MiniLM-L6-v2` model. Results are filtered based on configured minimum similarity scores (`element.retrieval.min.target.score`
   for high confidence, `element.retrieval.min.general.score` for potential matches) and `element.retrieval.min.page.relevance.score` for
   relevance to the current page.
2. **Handling Retrieval Results:**
    * **High-Confidence Match(es) Found:** If one or more elements exceed the `MIN_TARGET_RETRIEVAL_SCORE` and/or
      `MIN_PAGE_RELEVANCE_SCORE`:
        * **Hybrid Visual Matching:**
            * A vision model is used to identify potential bounding boxes for UI elements that visually resemble the target element on the
              current screen.
            * Concurrently, OpenCV's ORB and Template Matching algorithms are used to find additional visual matches of the element's stored
              screenshot on the current screen.
            * The results from both the vision model and algorithmic matching are combined and analyzed to find common or best-fitting
              bounding boxes.
        * **Disambiguation (if needed):** If multiple candidate bounding boxes are found, the vision model is employed to select the single
          best match that corresponds to the target element's
          description and the description of surrounding elements (anchors), based on a screenshot showing all candidate bounding boxes
          highlighted with distinctly colored labels.
    * **Low-Confidence/No Match(es) Found:** If no elements meet the `MIN_TARGET_RETRIEVAL_SCORE` or `MIN_PAGE_RELEVANCE_SCORE`, but some
      meet the
      `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Attended Mode:** The agent displays a popup showing a list of the low-scoring potential UI element candidates. The user can
          choose to:
            * **Update** one of the candidates by refining its name, description, anchors, or page summary and save the updated information
              to the
              vector DB.
            * **Delete** a deprecated element from the vector DB.
            * **Create New Element** (see below).
            * **Retry Search** (useful if elements were manually updated).
            * **Terminate** the test execution (e.g., due to an AUT bug).
        * **Unattended Mode:** The location process fails.
    * **No Matches Found:** If no elements meet even the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Attended Mode:** The user is guided through the new element creation flow:
            1. The user draws a bounding box around the target element on a full-screen capture.
            2. The captured element screenshot with its description are sent to the vision model to generate a suggested detailed name,
               self-description, surrounding elements (anchors) description, and page summary.
            3. The user reviews and confirms/edits the information suggested by the model.
            4. The new `UiElement` record (with UUID, name, descriptions, page summary, screenshot) is stored into the vector DB.
        * **Unattended Mode:** The location process fails.

## Setup Instructions

### Prerequisites

* Java Development Kit (JDK) - Version 24 or later recommended.
* Apache Maven - For building the project.
* Chroma vector database (the only one supported for now).
* Subscription to an AI model provider (Google Cloud/AI Studio, Azure OpenAI, or Groq).

### Maven Setup

This project uses Maven for dependency management and building.

1. **Clone the Repository:**
   ```bash
   git clone <repository_url>
   cd <project_directory>
   ```

2. **Build the Project:**
   ```bash
   mvn clean package
   ```
   This command downloads dependencies, compiles the code, runs tests (if any), and packages the application into a standalone JAR file in
   the `target/` directory.

### Vector DB Setup

Instructions for setting up the currently only one supported vector database Chroma DB could be found on its official website.

### Configuration

Configure the agent by editing the [config.properties](src/main/resources/config.properties) file or by setting environment variables. *
*Environment variables
override properties file settings.**

**Key Configuration Properties:**

* `unattended.mode` (Env: `UNATTENDED_MODE`): `true` for unattended execution, `false` for attended (trainee) mode. Default: `false`.
* `debug.mode` (Env: `DEBUG_MODE`): `true` enables debug mode, which saves intermediate screenshots (e.g., with bounding boxes drawn) 
  during element location for debugging purposes. `false` disables this. Default: `false`.
* `port` (Env: `PORT`): Port for the server mode. Default: `8005`.
* `host` (Env: `AGENT_HOST`): Host address for the server mode. Default: `localhost`.
* `vector.db.provider` (Env: `VECTOR_DB_PROVIDER`): Vector database provider. Default: `chroma`.
* `vector.db.url` (Env: `VECTOR_DB_URL`): Required URL for the vector database connection. Default: `http://localhost:8020`.
* `retriever.top.n` (Env: `RETRIEVER_TOP_N`): Number of top similar elements to retrieve from the vector DB based on semantic element name
  similarity. Default: `5`.
* `model.provider` (Env: `MODEL_PROVIDER`): AI model provider (`google`, `openai`, or `groq`). Default: `google`.
* `instruction.model.name` (Env: `INSTRUCTION_MODEL_NAME`): Name/deployment ID of the model for processing test case actions and
  verifications. Default: `gemini-2.5-flash`.
* `vision.model.name` (Env: `VISION_MODEL_NAME`): Name/deployment ID of the vision-capable model. Default: `gemini-2.5-flash`.
* `model.max.output.tokens` (Env: `MAX_OUTPUT_TOKENS`): Maximum amount of tokens for model responses. Default: `8192`.
* `model.temperature` (Env: `TEMPERATURE`): Sampling temperature for model responses. Default: `0.0`.
* `model.top.p` (Env: `TOP_P`): Top-P sampling parameter. Default: `1.0`.
* `model.max.retries` (Env: `MAX_RETRIES`): Max retries for model API calls. Default: `10`.
* `model.logging.enabled` (Env: `LOG_MODEL_OUTPUT`): Enable/disable model logging. Default: `false`.
* `thinking.output.enabled` (Env: `OUTPUT_THINKING`): Enable/disable thinking process output. Default: `true`.
* `gemini.thinking.budget` (Env: `GEMINI_THINKING_BUDGET`): Budget for Gemini thinking process. Default: `0`.
* `google.api.provider` (Env: `GOOGLE_API_PROVIDER`): Google API provider (`studio_ai` or `vertex_ai`). Default: `studio_ai`.
* `google.api.token` (Env: `GOOGLE_AI_TOKEN`): API Key for Google AI Studio. Required if using AI Studio.
* `google.project` (Env: `GOOGLE_PROJECT`): Google Cloud Project ID. Required if using Vertex AI.
* `google.location` (Env: `GOOGLE_LOCATION`): Google Cloud location (region). Required if using Vertex AI.
* `azure.openai.api.key` (Env: `OPENAI_API_KEY`): API Key for Azure OpenAI. Required if using OpenAI.
* `azure.openai.endpoint` (Env: `OPENAI_API_ENDPOINT`): Endpoint URL for Azure OpenAI. Required if using OpenAI.
* `groq.api.key` (Env: `GROQ_API_KEY`): API Key for Groq. Required if using Groq.
* `groq.endpoint` (Env: `GROQ_ENDPOINT`): Endpoint URL for Groq. Required if using Groq.
* `test.step.execution.retry.timeout.millis` (Env: `TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed test case
  actions. Default: `5000 ms`.
* `test.step.execution.retry.interval.millis` (Env: `TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS`): Delay between test case action retries.
  Default: `1000 ms`.
* `verification.retry.timeout.millis` (Env: `VERIFICATION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed verifications. Default:
  `5000 ms`.
* `action.verification.delay.millis` (Env: `ACTION_VERIFICATION_DELAY_MILLIS`): Delay after executing a test case action before performing
  the corresponding verification. Default: `500 ms`.
* `element.bounding.box.color` (Env: `BOUNDING_BOX_COLOR`): Required color name (e.g., `green`) for the bounding box drawn during element
  capture in attended mode. This value should be tuned so that the color contrasts as much as possible with the average UI element color.
* `element.retrieval.min.target.score` (Env: `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score are treated as target element candidates and used for further disambiguation by a vision
  model. Default: `0.8`.
* `element.retrieval.min.general.score` (Env: `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score will be displayed to the operator in case they decide to update any of them (e.g., due to
  UI changes, etc.). Default: `0.5`.
* `element.retrieval.min.page.relevance.score` (Env: `ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE`): Minimum page relevance score for vector
  DB UI element retrieval. Default: `0.5`.
* `element.locator.visual.similarity.threshold` (Env: `VISUAL_SIMILARITY_THRESHOLD`): OpenCV template matching threshold. Default: `0.8`.
* `element.locator.top.visual.matches` (Env: `TOP_VISUAL_MATCHES_TO_FIND`): Maximum number of visual matches of a single UI element from
  OpenCV to pass to the AI model for disambiguation. Default: `6`.
* `element.locator.min.intersection.area.ratio` (Env: `MIN_INTERSECTION_PERCENTAGE`): Minimum intersection area ratio for a visual match to
  be considered valid. Default: `0.8`.
* `element.locator.found.matches.dimension.deviation.ratio` (Env: `FOUND_MATCHES_DIMENSION_DEVIATION_RATIO`): Maximum allowed deviation
  ratio for the dimensions of a found visual match compared to the original element. Default: `0.3`.
* `dialog.default.horizontal.gap`, `dialog.default.vertical.gap`, `dialog.default.font.type`,
  `dialog.user.interaction.check.interval.millis`, `dialog.default.font.size`: Cosmetic and timing settings for interactive dialogs.

## How to Run

### Standalone Mode

Runs a single test case defined in a JSON file.

1. Ensure the project is built (`mvn clean package`).
2. Create a JSON file containing the test case (see [this one](src/test/resources/use_case.json) for an example).
3. Run the `Agent` class directly using Maven Exec Plugin (add configuration to `pom.xml` if needed):
   ```bash
   mvn exec:java -Dexec.mainClass="org.tarik.ta.Agent" -Dexec.args="<path/to/your/testcase.json>"
   ```
   Or run the packaged JAR:
   ```bash
   java -jar target/<your-jar-name.jar> <path/to/your/testcase.json>
   ```

### Server Mode

Starts a web server that listens for test case execution requests.

1. Ensure the project is built.
2. Run the `Server` class using Maven Exec Plugin:
   ```bash
   mvn exec:java -Dexec.mainClass="org.tarik.ta.Server"
   ```
   Or run the packaged JAR:
   ```bash
   java -jar target/<your-jar-name.jar>
   ```
3. The server will start listening on the configured port (default `8005`).
4. Send a `POST` request to the root endpoint (`/`) with the test case JSON in the request body.
5. The server will respond immediately with `200 OK` if it accepts the request (i.e., not already running a test case) or
   `429 Too Many Requests` if it's busy. The test case execution runs asynchronously.

## Deployment

This section provides detailed instructions for deploying the UI Test Automation Agent, both to Google Cloud Platform (GCP) and locally using Docker.

### Cloud Deployment (Google Compute Engine)

The agent can be deployed as a containerized application on a Google Compute Engine (GCE) virtual machine, providing a robust and scalable environment for automated UI testing.

#### Prerequisites for Cloud Deployment

*   **Google Cloud Project:** An active GCP project with billing enabled.
*   **gcloud CLI:** The Google Cloud SDK `gcloud` command-line tool installed and configured.
*   **Secrets in Google Secret Manager:** The following secrets must be created in Google Secret Manager within your GCP project. These are crucial for the agent's operation and should be stored securely.
    *   `GROQ_API_KEY`: Your API key for Groq.
    *   `GROQ_ENDPOINT`: The endpoint URL for Groq.
    *   `VECTOR_DB_URL`: The URL of your Chroma DB instance.
    *   `VNC_PW`: The password for accessing the VNC session.

    You can create these secrets using `gcloud` commands. For example:
    ```bash
    gcloud secrets create GROQ_API_KEY --replication-policy="automatic" --project=<your-gcp-project-id>
    echo -n "your-groq-api-key" | gcloud secrets versions add GROQ_API_KEY --data-file=- --project=<your-gcp-project-id>
    # Repeat for GROQ_ENDPOINT, VECTOR_DB_URL, and VNC_PW
    ```

#### Deploying Chroma DB (Vector Database)

The agent relies on Chroma DB as its vector database. You can deploy Chroma DB to Google Cloud Run using the provided `cloudbuild_chroma.yaml` configuration.

1.  **Configure `cloudbuild_chroma.yaml`:**
    *   Update `_CHROMA_BUCKET` with the name of a Google Cloud Storage bucket where Chroma DB will store its data.
    *   Update `_CHROMA_DATA_PATH` if you want a specific path within the bucket.
    *   Update `_PORT` if you want Chroma DB to run on a different port (default is `8000`).

2.  **Deploy using Cloud Build:**
    ```bash
    gcloud builds submit . --config deployment/cloudbuild_chroma.yaml --substitutions=_CHROMA_BUCKET=<your-chroma-bucket-name>,_CHROMA_DATA_PATH=chroma,_PORT=8000 --project=<your-gcp-project-id>
    ```
    After deployment, note the URL of the deployed Chroma DB service; this will be your `VECTOR_DB_URL`.

#### Building and Deploying the Agent on GCE

The `deploy_gce.sh` script automates the process of building the Docker image for the agent and provisioning a GCE VM.

1.  **Navigate to the project root:**
    ```bash
    cd <project_root_directory>
    ```

2.  **Execute the deployment script:**
    ```bash
    ./deployment/cloud/deploy_gce.sh
    ```
    This script will:
    *   Enable necessary GCP services.
    *   Build the Java application using Maven.
    *   Build the Docker image for the agent using `deployment/cloud/Dockerfile.cloudrun` and push it to Google Container Registry.
    *   Set up VPC network and firewall rules (if they don't exist).
    *   Create a GCE instance with the agent container running.

    **Note:** The script uses default values for region, zone, instance name, etc. You can override these by setting environment variables before running the script (e.g., `export REGION="europe-west1"`).

#### Accessing the Deployed Agent

*   **Agent Server:** The agent will be running on the port configured by `AGENT_SERVER_PORT` (default `443`). The internal hostname can be retrieved by executing `curl "http://metadata.google.internal/computeMetadata/v1/instance/hostname" -H "Metadata-Flavor: Google"` inside the VM.
*   **noVNC Access:** You can access the agent's desktop environment via noVNC in your web browser. The URL will be `https://<EXTERNAL_IP>:<NO_VNC_PORT>`, where `<EXTERNAL_IP>` is the external IP of your GCE instance and `<NO_VNC_PORT>` is the noVNC port (default `6901`). The VNC password is set via the `VNC_PW` secret.

### Local Docker Deployment

For local development and testing, you can run the agent within a Docker container on your machine.

#### Prerequisites for Local Docker Deployment

*   **Docker Desktop:** Ensure Docker Desktop is installed and running on your system.

#### Building and Running the Docker Image

The `build_and_run_docker.bat` script (for Windows) simplifies the process of building the Docker image and running the container.

1.  **Open `deployment/local/Dockerfile`:**
    *   **IMPORTANT:** Before running the script, open `deployment/local/Dockerfile` and replace the placeholder `VNC_PW` environment variable with a strong password of your choice. For example:
        ```dockerfile
        ENV VNC_PW="your_strong_vnc_password"
        ```
        (Note: The `build_and_run_docker.bat` script also sets `VNC_PW` to `123456` for convenience, but it's recommended to set it directly in the Dockerfile for consistency and security.)

2.  **Execute the batch script:**
    ```bash
    deployment\local\build_and_run_docker.bat
    ```
    This script will:
    *   Build the Docker image named `ui-test-automation-agent` using `deployment/local/Dockerfile`.
    *   Stop and remove any existing container named `ui-agent`.
    *   Run a new Docker container, mapping ports `5901` (VNC), `6901` (noVNC), and `8005` (agent server) to your local machine.

#### Accessing the Local Agent

*   **VNC Client:** You can connect to the VNC session using a VNC client at `localhost:5901`.
*   **noVNC (Web Browser):** Access the agent's desktop environment via your web browser at `http://localhost:6901/vnc.html`.
*   **Agent Server:** The agent's server will be accessible at `http://localhost:8005`.

Remember to use the VNC password you set in the Dockerfile when prompted.

## Deployment

This section provides detailed instructions for deploying the UI Test Automation Agent, both to Google Cloud Platform (GCP) and locally using Docker.

### Cloud Deployment (Google Compute Engine)

The agent can be deployed as a containerized application on a Google Compute Engine (GCE) virtual machine, providing a robust and scalable environment for automated UI testing.

#### Prerequisites for Cloud Deployment

*   **Google Cloud Project:** An active GCP project with billing enabled.
*   **gcloud CLI:** The Google Cloud SDK `gcloud` command-line tool installed and configured.
*   **Secrets in Google Secret Manager:** The following secrets must be created in Google Secret Manager within your GCP project. These are crucial for the agent's operation and should be stored securely.
    *   `GROQ_API_KEY`: Your API key for Groq.
    *   `GROQ_ENDPOINT`: The endpoint URL for Groq.
    *   `VECTOR_DB_URL`: The URL of your Chroma DB instance.
    *   `VNC_PW`: The password for accessing the VNC session.

    You can create these secrets using `gcloud` commands. For example:
    ```bash
    gcloud secrets create GROQ_API_KEY --replication-policy="automatic" --project=<your-gcp-project-id>
    echo -n "your-groq-api-key" | gcloud secrets versions add GROQ_API_KEY --data-file=- --project=<your-gcp-project-id>
    # Repeat for GROQ_ENDPOINT, VECTOR_DB_URL, and VNC_PW
    ```

#### Deploying Chroma DB (Vector Database)

The agent relies on Chroma DB as its vector database. You can deploy Chroma DB to Google Cloud Run using the provided `cloudbuild_chroma.yaml` configuration.

1.  **Configure `cloudbuild_chroma.yaml`:**
    *   Update `_CHROMA_BUCKET` with the name of a Google Cloud Storage bucket where Chroma DB will store its data.
    *   Update `_CHROMA_DATA_PATH` if you want a specific path within the bucket.
    *   Update `_PORT` if you want Chroma DB to run on a different port (default is `8000`).

2.  **Deploy using Cloud Build:**
    ```bash
    gcloud builds submit . --config deployment/cloudbuild_chroma.yaml --substitutions=_CHROMA_BUCKET=<your-chroma-bucket-name>,_CHROMA_DATA_PATH=chroma,_PORT=8000 --project=<your-gcp-project-id>
    ```
    After deployment, note the URL of the deployed Chroma DB service; this will be your `VECTOR_DB_URL`.

#### Building and Deploying the Agent on GCE

The `deploy_gce.sh` script automates the process of building the Docker image for the agent and provisioning a GCE VM.

1.  **Navigate to the project root:**
    ```bash
    cd <project_root_directory>
    ```

2.  **Execute the deployment script:**
    ```bash
    ./deployment/cloud/deploy_gce.sh
    ```
    This script will:
    *   Enable necessary GCP services.
    *   Build the Java application using Maven.
    *   Build the Docker image for the agent using `deployment/cloud/Dockerfile.cloudrun` and push it to Google Container Registry.
    *   Set up VPC network and firewall rules (if they don't exist).
    *   Create a GCE instance with the agent container running.

    **Note:** The script uses default values for region, zone, instance name, etc. You can override these by setting environment variables before running the script (e.g., `export REGION="europe-west1"`).

#### Accessing the Deployed Agent

*   **Agent Server:** The agent will be running on the port configured by `AGENT_SERVER_PORT` (default `443`). The internal hostname can be retrieved by executing `curl "http://metadata.google.internal/computeMetadata/v1/instance/hostname" -H "Metadata-Flavor: Google"` inside the VM.
*   **noVNC Access:** You can access the agent's desktop environment via noVNC in your web browser. The URL will be `https://<EXTERNAL_IP>:<NO_VNC_PORT>`, where `<EXTERNAL_IP>` is the external IP of your GCE instance and `<NO_VNC_PORT>` is the noVNC port (default `6901`). The VNC password is set via the `VNC_PW` secret.


### Local Docker Deployment

For local development and testing, you can run the agent within a Docker container on your machine.

#### Prerequisites for Local Docker Deployment

*   **Docker Desktop:** Ensure Docker Desktop is installed and running on your system.

#### Building and Running the Docker Image

The `build_and_run_docker.bat` script (for Windows) simplifies the process of building the Docker image and running the container.

1.  **Open `deployment/local/Dockerfile`:**
    *   **IMPORTANT:** Before running the script, open `deployment/local/Dockerfile` and replace the placeholder `VNC_PW` environment variable with a strong password of your choice. For example:
        ```dockerfile
        ENV VNC_PW="your_strong_vnc_password"
        ```
        (Note: The `build_and_run_docker.bat` script also sets `VNC_PW` to `123456` for convenience, but it's recommended to set it directly in the Dockerfile for consistency and security.)

2.  **Execute the batch script:**
    ```bash
    deployment\local\build_and_run_docker.bat
    ```
    This script will:
    *   Build the Docker image named `ui-test-automation-agent` using `deployment/local/Dockerfile`.
    *   Stop and remove any existing container named `ui-agent`.
    *   Run a new Docker container, mapping ports `5901` (VNC), `6901` (noVNC), and `8005` (agent server) to your local machine.

#### Accessing the Local Agent

*   **VNC Client:** You can connect to the VNC session using a VNC client at `localhost:5901`.
*   **noVNC (Web Browser):** Access the agent's desktop environment via your web browser at `http://localhost:6901/vnc.html`.
*   **Agent Server:** The agent's server will be accessible at `http://localhost:8005`.

Remember to use the VNC password you set in the Dockerfile when prompted.

## Contributing

Please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for guidelines on contributing to this project.

## TODOs

* Add public method comments and unit tests.
* Add public unit tests for at least 80% coverage.
* Implement quorum of different models (vision experts) in order to get more accurate verification results.
* Extend UiElement in DB so that it has info if it's bound to any test data, or is independent of it. In this way the element search
  algorithm must take into account the specific test data and replace the element description/name/anchors template info with this data.
  The visual similarity for pattern match must also be adapted (lowered) in such cases because element screenshot will contain specific
  test data.

## Final Notes

* **Project Scope:** This project is developed as a prototype of an agent, a minimum working example, and thus a basis for further
  extensions and enhancements. It's not a production-ready instance or a product developed according to all the requirements/standards
  of an SDLC (however many of them have been taken into account during development).
* **Environment:** The agent has been manually tested on the Windows 11 platform. There are issues with OpenCV and OpenBLAS libraries
  running on Linux, but there is no solution to those issues yet.
* **Standalone Executable Size:** The standalone JAR file can be quite large (at least ~330 MB). This is primarily due to the automatic
  inclusion of the ONNX embedding model (`all-MiniLM-L6-v2`) as a dependency of LangChain4j, and the native OpenCV libraries required for
  visual element location.
* **Bounding Box Colors:** When multiple visual matches are found for element disambiguation, the agent assigns different bounding box
  color to each match in order to uniquely label the element. There are a limited number of predefined colors (`availableBoundingBoxColors`
  field in [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java)). If more visual matches are found than available colors,
  an error will occur. This might happen if the `element.locator.visual.similarity.threshold` is too low or if there are many visually
  similar elements on the screen (e.g., the same check-boxes for a list of items). You might need to use a different labelling method for
  visual matches in this case (the primary approach during development of this project was to use numbers located outside the bounding box
  as labels, which, however, proved to be less efficient compared to using different bounding box colors, but is still a good option if the
  latter cannot be applied).
* **Unit Tests:** Currently, the project lacks comprehensive unit tests due to time constraints during initial development. Adding unit
  tests is crucial for maintainability and stability. Therefore, all future contributions and pull requests to the `main` branch **should**
  include relevant unit tests. Contributing by adding new unit tests to existing code is, as always, welcome.