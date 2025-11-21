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
    java -jar build/libs/Impact-Analyzer.jar
    # OR using the Spring Boot Gradle Plugin:
    ./gradlew bootRun
    ```

The application will start on the default port **`8080`**.

---

## 📂 Project Structure for Testing

To run the analysis successfully, you must create a local directory structure simulating the two Git commits (Baseline and Target).

### Local Test Directory Setup

Create a git repo/or use public repos where we need to look for impact for your test data (e.g., `mock_test_repo`). 

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

{

  "selectedRepository": "https://github.com/testhackathon01-gif/order-purchase",
  
  "compareRepositoryUrls": [
  
    "https://github.com/testhackathon01-gif/order-purchase"
    
  ],
  
  "targetFilename": "PricingUtility.java",
  
  "changedCode": "package com.app.finance;\n\nimport java.math.BigDecimal;\nimport java.math.RoundingMode;\n\npublic class PricingUtility {\n\tprivate static final double TAX_RATE = 0.05; // 5% tax\n\n\t// 1. ORIGINAL: Returns double.\n\tpublic BigDecimal calculateDiscount(double price, double percentage) {\n\t\tBigDecimal discount = BigDecimal.valueOf(price)\n\t\t\t\t.multiply(BigDecimal.valueOf(percentage))\n\t\t\t\t.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);\n\t\treturn discount;\n\t}\n\t// 2. ORIGINAL: Simple tax rate getter.\n\tpublic double getTaxRate() {\n\t\treturn TAX_RATE;\n\t}\n\n\t// 3. DEAD CODE: This method is never called by dependents.\n\tpublic String getProductCodePrefix() {\n\t\treturn \"PRD_V1_\";\n\t}\n}"
  
}


**Response : **

[

  {
  
    "changedMember": "calculateDiscount",
    
    "memberType": "METHOD",
    
    "riskScore": 8,
    
    "summaryReasoning": "Step 1: Analyze Contractual Change in Module A",
    
    "testStrategy": {
    
      "scope": "Modules requiring syntactic fixes, semantic validation for precision, and runtime null-safety validation for BigDecimal type changes. Additionally, the core utility method's new BigDecimal logic must be thoroughly tested.",
      
      "priority": "HIGH",
      
      "testCasesRequired": [
      
        {
        
          "moduleName": "com.app.order.OrderProcessor",
          
          "testType": "Integration Test",
          
          "focus": "Verify `calculateDiscount` integration, ensuring correct `BigDecimal` handling and precision preservation after fixing the syntactic break. Test various order totals and discount percentages."
          
        }
        
    ]
    
},

    "actionableImpacts": [
    
      {
      
        "moduleName": "com.app.order.OrderProcessor",
        
        "impactType": "SYNTACTIC_BREAK",
        
        "issue": "The `calculateDiscount` method now returns `BigDecimal` instead of `double`."
        
      }
      
    ]
    
}


curl -X POST http://localhost:8080/api/v1/impact/analyze \
-H 'Content-Type: application/json' \
