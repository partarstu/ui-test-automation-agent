# AI-Powered UI Test Automation Agent

This project is a Java-based agent that leverages Generative AI models and Retrieval-Augmented Generation (RAG) to execute automated test
cases at the graphical user interface (GUI) level. It understands explicit natural language test case instructions (both actions and
verifications), performs corresponding actions using the mouse and keyboard, locates the required UI elements on the screen (if needed), and
verifies whether actual results correspond to the expected ones using computer vision capabilities.

[![Package Project](https://github.com/partarstu/ui-test-automation-agent/actions/workflows/package.yml/badge.svg)](https://github.com/partarstu/ui-test-automation-agent/actions/workflows/package.yml)

Here the corresponding article on Medium: [AI Agent That’s Rethinking UI Test Automation](https://medium.com/@partarstu/meet-the-ai-agent-thats-rethinking-ui-test-automation-d8ef9742c6d5)

## Key Features

* **AI Model Integration:**
    * Utilizes the [LangChain4j](https://github.com/langchain4j/langchain4j) library to seamlessly interact with various Generative AI
      models.
    * Supports models from Google (via AI Studio or Vertex AI) and Azure OpenAI. Configuration is managed through `config.properties` and
      `AgentConfig.java`, allowing specification of providers, model names (`instruction.model.name`, `vision.model.name`), API keys/tokens,
      endpoints, and generation parameters (temperature, topP, max output tokens, retries).
    * Leverages separate models for instruction understanding (test case actions and verifications) and vision-based tasks like locating the
      best matching UI element, suggesting a new UI element's description, and verifying actual vs. expected results.
    * Uses structured prompts to guide model responses and ensure output can be parsed into required DTOs.

* **RAG:**
    * Employs a Retrieval-Augmented Generation (RAG) approach to manage information about UI elements.
    * Uses a vector database to store and retrieve UI element details (name, element description, anchor element descriptions, and
      screenshot). It currently supports only Chroma DB (`AgentConfig.getVectorDbProvider` -> `chroma`), configured via `vector.db.url` in
      `config.properties`.
    * Stores UI element information as `UiElement` records, which include a name, self-description, description of surrounding
      elements (anchors), and a screenshot (`UiElement.Screenshot`).
    * Retrieves the top N (`retriever.top.n` in config) most relevant UI elements based on semantic similarity between the query (derived
      from the test step action) and based on the stored element names. Minimum similarity scores (`element.retrieval.min.target.score`,
      `element.retrieval.min.general.score` in config) are used to filter results for target element identification and potential refinement
      suggestions.

* **Computer Vision:**
    * Uses the OpenCV library (via `org.bytedeco.opencv`) for visual template matching to locate UI elements on the screen based on the
      element screenshot provided during the first attended test case execution.
    * Leverages a vision-capable AI model (`ModelFactory.getVisionModel`) to disambiguate when multiple visual matches are found or to
      confirm that a single visual match, if found, corresponds to the target element's description and surrounding element information.

* **GUI Interaction Tools:**
    * Provides a set of [tools](src/main/java/org/tarik/ta/tools) for interacting with the GUI using Java's `Robot` class.
    * [MouseTools](src/main/java/org/tarik/ta/tools/MouseTools.java) offer actions for working with the mouse (clicks, hover,
      click-and-drag, etc.). These tools typically locate the target UI element first,
      using [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java).
    * [KeyboardTools](src/main/java/org/tarik/ta/tools/KeyboardTools.java) provide actions for working with the keyboard (typing text,
      pressing single keys or key combinations, etc.).
    * [CommonTools](src/main/java/org/tarik/ta/tools/CommonTools.java) include common actions like waiting for a specified duration.

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
        * **Server Mode:** This mode allows the agent to be accessed as part of an agent "swarm" executing test cases in a distributed
          manner. The [Server](src/main/java/org/tarik/ta/Server.java) class is the entry point where a Javalin web server is started.
          Generating test case execution results, registering within the swarm, or sending a heartbeat has not been implemented yet. The
          server listens for HTTP POST requests on the `/testcase` endpoint (port configured via `port` in `config.properties`). The request
          body should contain the test case in JSON format. The server accepts only one test case execution at a time (the agent has been
          designed as a static utility for simplicity purposes). Upon receiving a valid request when idle, it returns `200 OK` and starts
          the test case execution. If busy, it returns `429 Too Many Requests`.

## Test Case Execution Workflow

The test execution process, orchestrated by the `Agent` class, follows these steps:

1. **Test Case Processing:** The agent loads the test case defined in a JSON file (e.g., [this one](src/test/resources/use_case.json)).
   This file contains the overall test case name and a list of `TestStep`s. Each `TestStep` includes a `stepDescription` (natural language
   instruction), optional `testData` (inputs for the step), and `expectedResults` (natural language description of the expected state after
   the step).
2. **Step Iteration:** The agent iterates through each `TestStep` sequentially.
3. **Action Processing (for each Action step):**
    * **Screenshot:** A screenshot of the current screen is taken.
    * **Instruction Model Interaction:** The action execution prompt is sent to the model with the test step action description, the current
      screenshot, and any `testData`. The model analyzes the action and determines which tool(s) to call and with what arguments (including
      extracting the description of the target UI element if needed). The response is expected to contain a selected tool.
    * **Tool Execution:** The appropriate tool method with the arguments provided by the model is invoked.
    * **Element Location (if required by the tool):** If the requested tool needs to interact with a specific UI element (e.g., clicking an
      element), the element is located using the [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java) class based on the
      element's description (provided as a parameter for the tool). (See "UI Element Location Workflow" below for details).
    * **Retry/Rerun Logic:** If a tool execution reports that retrying makes sense (e.g., an element was not found on the screen), the
      agent retries the execution after a short delay, up to a configured timeout (`test.step.execution.retry.timeout.millis`). If the
      error persists after the deadline, the test case execution is interrupted.
4. **Verification Processing (for each Verification step):**
    * **Delay:** A short delay (`action.verification.delay.millis`) is introduced to allow the UI state to change after the preceding
      action.
    * **Screenshot:** A screenshot of the current screen is taken.
    * **Vision Model Interaction:** A verification prompt containing the expected results description and the current screenshot is sent to
      the configured vision AI model. The model analyzes the screenshot and compares it against the expected results description.
    * **Result Parsing:** The model's response contains information indicating whether the verification passed, and extended information
      with the justification for the result.
    * **Retry Logic:** If the verification fails, the agent retries the verification process after a short interval (
      `test.step.execution.retry.interval.millis`) until a timeout (`verification.retry.timeout.millis`) is reached. If it still fails after
      the deadline, the test case execution is interrupted.
5. **Completion/Termination:** Execution continues until all steps are processed successfully or an interruption (error, verification
   failure, user termination) occurs.

### UI Element Location Workflow

The [ElementLocator](src/main/java/org/tarik/ta/tools/ElementLocator.java) class is responsible for finding the coordinates of a target UI
element based on its natural language description provided by the instruction model during an action step. This involves a combination of
RAG, computer vision, analysis, and potentially user interaction (if run in attended mode):

1. **RAG Retrieval:** The provided UI element's description is used to query the vector database, where the top N (`retriever.top.n`) most
   semantically similar `UiElement` records are retrieved based on their stored names, using embeddings generated by
   the `all-MiniLM-L6-v2` model. Results are filtered based on configured minimum similarity scores (`element.retrieval.min.target.score`
   for high confidence, `element.retrieval.min.general.score` for potential matches).
2. **Handling Retrieval Results:**
    * **High-Confidence Match(es) Found:** If one or more elements exceed the `MIN_TARGET_RETRIEVAL_SCORE`:
        * **Visual Template Matching:** OpenCV's template matching function is used to find potential occurrences of each high-confidence
          element's stored screenshot on the current screen image. Multiple matches might be found for a single element screenshot due to
          visual similarities or repeating patterns if they exceed the similarity threshold (`element.locator.visual.similarity.threshold`).
        * **Disambiguation (if needed):** The vision model is employed to find the single element that matches the target element's
          description and the description of surrounding elements (anchors), based on the screenshot showing all found visual matches
          highlighted with distinctly colored bounding boxes. If the vision model identified no match at all, user
          interaction is required in attended mode (see below); otherwise, the element location returns no results.
    * **Low-Confidence/No Match(es) Found:** If no elements meet the `MIN_TARGET_RETRIEVAL_SCORE`, but some meet the
      `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Attended Mode:** The agent displays a popup showing a list of the low-scoring potential UI element candidates. The user can
          choose to:
            * **Update** one of the candidates by refining its name, description, or anchors and save the updated information to the
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
               self-description, and surrounding elements (anchors) description.
            3. The user reviews and confirms/edits the information suggested by the model.
            4. The new `UiElement` record (with UUID, name, descriptions, screenshot) is stored into the vector DB.
        * **Unattended Mode:** The location process fails.

## Setup Instructions

### Prerequisites

* Java Development Kit (JDK) - Version 24 or later recommended.
* Apache Maven - For building the project.
* Chroma vector database (the only one supported for now).
* Subscription to an AI model provider (currently only Google Cloud/AI Studio or Azure OpenAI are supported).

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

Configure the agent by editing the [config.properties](src/main/resources/config.properties) file or by setting environment variables. **Environment variables
override properties file settings.**

**Key Configuration Properties:**

* `unattended.mode` (Env: `UNATTENDED_MODE`): `true` for unattended execution, `false` for attended (trainee) mode. Default: `false`.
* `test.mode` (Env: `TEST_MODE`): `true` enables test mode, which saves intermediate screenshots (e.g., with bounding boxes drawn) during
  element location for debugging purposes. `false` disables this. Default: `false`.
* `port` (Env: `PORT`): Port for the server mode. Default: `7070`.
* `vector.db.provider` (Env: `VECTOR_DB_PROVIDER`): Vector database provider. Default: `chroma`.
* `vector.db.url` (Env: `VECTOR_DB_URL`): Required URL for the vector database connection.
* `retriever.top.n` (Env: `RETRIEVER_TOP_N`): Number of top similar elements to retrieve from the vector DB based on semantic element name
  similarity. Default: `3`.
* `model.provider` (Env: `MODEL_PROVIDER`): AI model provider (`google` or `openai`). Default: `google`.
* `instruction.model.name` (Env: `INSTRUCTION_MODEL_NAME`): Name/deployment ID of the model for processing test case actions and
  verifications.
* `vision.model.name` (Env: `VISION_MODEL_NAME`): Name/deployment ID of the vision-capable model.
* `model.max.output.tokens` (Env: `MAX_OUTPUT_TOKENS`): Maximum amount of tokens for model responses. Default: `5000`.
* `model.temperature` (Env: `TEMPERATURE`): Sampling temperature for model responses. Default: `0.0`.
* `model.top.p` (Env: `TOP_P`): Top-P sampling parameter. Default: `1.0`.
* `model.max.retries` (Env: `MAX_RETRIES`): Max retries for model API calls. Default: `10`.
* `google.api.provider` (Env: `GOOGLE_API_PROVIDER`): Google API provider (`studio_ai` or `vertex_ai`). Default: `studio_ai`.
* `google.api.token` (Env: `GOOGLE_AI_TOKEN`): API Key for Google AI Studio. Required if using AI Studio.
* `google.project` (Env: `GOOGLE_PROJECT`): Google Cloud Project ID. Required if using Vertex AI.
* `google.location` (Env: `GOOGLE_LOCATION`): Google Cloud location (region). Required if using Vertex AI.
* `openai.api.key` (Env: `OPENAI_API_KEY`): API Key for Azure OpenAI. Required if using OpenAI.
* `openai.api.endpoint` (Env: `OPENAI_API_ENDPOINT`): Endpoint URL for Azure OpenAI. Required if using OpenAI.
* `test.step.execution.retry.timeout.millis` (Env: `TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed test case
  actions. Default: `10000 ms`.
* `test.step.execution.retry.interval.millis` (Env: `TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS`): Delay between test case action retries.
  Default: `1000 ms`.
* `verification.retry.timeout.millis` (Env: `VERIFICATION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed verifications. Default:
  `10000 ms`.
* `action.verification.delay.millis` (Env: `ACTION_VERIFICATION_DELAY_MILLIS`): Delay after executing a test case action before performing
  the corresponding verification. Default: `1000 ms`.
* `element.bounding.box.color` (Env: `BOUNDING_BOX_COLOR`): Required color name (e.g., `green`) for the bounding box drawn during element
  capture in attended mode. This value should be tuned so that the color contrasts as much as possible with the average UI element color.
* `element.retrieval.min.target.score` (Env: `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score are treated as target element candidates and used for further disambiguation by a vision
  model. Default: `0.85`.
* `element.retrieval.min.general.score` (Env: `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score will be displayed to the operator in case they decide to update any of them (e.g., due to
  UI changes, etc.). Default: `0.4`.
* `element.locator.visual.similarity.threshold` (Env: `VISUAL_SIMILARITY_THRESHOLD`): OpenCV template matching threshold. Default: `0.8`.
* `element.locator.top.visual.matches` (Env: `TOP_VISUAL_MATCHES_TO_FIND`): Maximum number of visual matches of a single UI element from
  OpenCV to pass to the AI model for disambiguation. Default: `3`.
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
3. The server will start listening on the configured port (default `7070`).
4. Send a `POST` request to the `/testcase` endpoint with the test case JSON in the request body.
5. The server will respond immediately with `200 OK` if it accepts the request (i.e., not already running a test case) or
   `429 Too Many Requests` if it's busy. The test case execution runs asynchronously.

## Contributing

Please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for guidelines on contributing to this project.

## TODOs
* Add public method comments and unit tests.
* Add public unit tests for at least 80% coverage.
* Extend agent to respond with meaningful execution results if run in the server mode.
* Extend UiElement in DB so that it has info if it's bound to any test data, or is independent of it. In this way the element search 
  algorithm must take into account the specific test data and replace the element description/name/anchors template info with this data. 
  The visual similarity for pattern match must also be adapted (lowered) in such cases because element screenshot will contain specific 
  test data.  
* Implement quorum of different models (vision experts) in order to get more accurate verification results.


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
