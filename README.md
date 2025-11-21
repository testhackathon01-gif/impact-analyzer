# 💥 Impact Analyzer API

An intelligent service designed to automatically analyze structural and semantic changes between two versions of Java source code (Baseline vs. Target) and determine the downstream impact on dependent modules.

This application uses JavaParser for Abstract Syntax Tree (AST) analysis to generate semantic diff snippets and simulates dependency resolution and LLM-based impact scoring.

---

## 🚀 Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

* **Java Development Kit (JDK) 17+**
* **Gradle** (or similar build tool used in your project setup)
* **Spring Boot** (Tested with Spring Boot 3+)

### Key Features

* **Semantic Analysis**: Utilizes JavaParser to generate Abstract Syntax Trees (ASTs), allowing the system to understand code logic rather than just syntax. It intelligently ignores non-functional changes like whitespace or formatting.
* **Semantic Diffing**: Detects structural differences in methods, fields, and signatures.
* **AI-Powered Risk Scoring**: Integrates with LLM - Gemini vertex AI to assign a quantitative risk score (1-10) to changes based on severity (e.g., Breaking API change vs. Internal logic update).
* **Reasoning Engine**: Translates technical diffs into human-readable explanations, detailing why a specific change is considered risky.
* **Impact Localization**: Identifies exactly which downstream modules and specific classes will be affected by a proposed modification.
* **JSON Reporting**: Outputs a comprehensive JSON report with actionable insights for CI/CD integration.

### Architecture

The analysis pipeline follows a 5-step workflow:

**Input**: Accepts Baseline and Target source code files.
**Parsing**: Converts raw source code into manipulatable Abstract Syntax Trees (AST).
**Diff Engine**: Identifies semantic and structural differences.
**Intelligence Layer**: LLM integration provides reasoning and calculates risk scores.
**Output**: Generates the final impact analysis report.


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

You will pass the path to `mock_test_repo` as the `localFilePath` in your API request.

---

## 💡 API Documentation (Swagger)

The application includes **Springdoc OpenAPI** for interactive documentation and testing.

| Description | URL |
| :--- | :--- |
| **Swagger UI** (Interactive Testing) | `http://localhost:8080/swagger-ui.html` |
| **OpenAPI Spec (JSON)** | `http://localhost:8080/v3/api-docs` |

---

## 🎯 API Endpoint: Analysis

The core functionality is exposed through a single POST endpoint.

### Endpoint Details

| Method | URL | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/impact/analyze` | Executes the semantic difference and impact assessment. |

### Request Body (`application/json`)

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `repositoryUrls` | `List<String>` | No | Placeholder for future integration (e.g., Git clone). |
| `localFilePath` | `String` | Yes | **The root path of the project folders** (e.g., `/path/to/mock_test_repo`). |
| `targetFilename` | `String` | Yes | **The specific file that was modified** (e.g., `A_Helper.java`). |

**Example Request Payload:**

```json
{
    "selectedRepository": "https://github.com/testhackathon01-gif/order-purchase",
    "compareRepositoryUrls": [
        "https://github.com/testhackathon01-gif/order-purchase"
    ],
    "localFilePath": "C:/Users/DELL/Downloads/order-purchase/src/main/java/",
    "targetFilename": "PricingUtility.java"
}

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
