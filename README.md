# 💥 Impact Analyzer REST API

An intelligent service designed to automatically analyze structural and semantic changes between two versions of Java source code (Baseline vs. Target) and determine the downstream impact on dependent modules.

This application uses JavaParser for Abstract Syntax Tree (AST) analysis to generate semantic diff snippets and simulates dependency resolution and LLM-based impact scoring.

---

## 🚀 Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

* **Java Development Kit (JDK) 17+**
* **Gradle** (or similar build tool used in your project setup)
* **Spring Boot** (Tested with Spring Boot 3+)

### Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/testhackathon01-gif/impact-analyzer.git
    cd impact-analyzer-api
    ```

2.  **Build the Project:**
    Compile the application using Gradle.
    ```bash
    ./gradlew clean build
    ```

3.  **Run the Application:**
    Execute the built JAR file.
    ```bash
    java -jar build/libs/IntelliDiffApplication.jar
    # OR using the Spring Boot Gradle Plugin:
    ./gradlew bootRun
    ```

The application will start on the default port **`8080`**.

---

## 📂 Project Structure for Testing

To run the analysis successfully, you must create a local directory structure simulating the two Git commits (Baseline and Target).

### Local Test Directory Setup

Create a base directory for your test data (e.g., `mock_test_repo`). Inside it, create two sub-folders:

[
{
"changedMethod": "getTimestamp",
"llmReport": {
"riskScore": 8,
"reasoning": "Dummy reasoning for getTimestamp change.",
"impactedModules": [
{
"moduleName": "com.app.modulea.DataGenerator",
"impactType": "SYNTACTIC_BREAK",
"description": "Return type mismatch."
}
]
}
}
]

curl -X POST http://localhost:8080/api/v1/impact/analyze \
-H 'Content-Type: application/json' \
-d '{"repositoryUrls": [], "localFilePath": "/path/to/your/mock_test_repo", "targetFilename": "A_Helper.java"}'