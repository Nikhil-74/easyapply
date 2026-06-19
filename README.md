# EasyApply

EasyApply is a Spring Boot application that helps streamline the job application process by combining resume analysis, job matching, recruiter outreach, email drafting, and LinkedIn automation.

## Features

* Resume upload and processing
* Job matching based on candidate profile
* AI-assisted email drafting
* Bulk recruiter email generation
* Email sending through SMTP
* LinkedIn automation using Selenium
* User profile management
* Resume attachment handling
* Local AI integration through Ollama

## Tech Stack

### Backend

* Java 21
* Spring Boot 3.5
* Spring Web
* Spring Mail

### AI

* Spring AI
* Ollama

### Automation

* Selenium WebDriver
* WebDriverManager

### Document Processing

* Apache PDFBox

### Utilities

* Jackson
* Lombok
* dotenv-java

## Project Structure

```text
src/main/java/com/easyapply
├── config
├── controller
├── dto
├── service
├── model
└── util
```

## Getting Started

### Prerequisites

* Java 21
* Maven 3.9+
* Ollama (optional, for AI features)
* Chrome browser

### Clone the Repository

```bash
git clone https://github.com/Nikhil-74/easyapply.git
cd easyapply
```

### Configure Application

Create or update:

```text
application-local.properties
```

Add the required configuration values:

```properties
# Mail Configuration
mail.username=your-email
mail.password=your-password

# User Configuration
user.name=Your Name

# AI Configuration
ollama.base-url=http://localhost:11434
```

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

Or:

```bash
java -jar target/easyapply-0.1.jar
```

## Key Modules

### User Profile

Manage candidate information used for job applications and email generation.

### Resume Processing

Extract and process resume content using Apache PDFBox.

### Email Automation

Generate and send recruiter emails individually or in bulk.

### LinkedIn Automation

Automate selected LinkedIn job application workflows using Selenium.

### AI-Assisted Drafting

Use local LLMs through Ollama to generate professional recruiter outreach emails.

## API Endpoints

The application exposes REST APIs for:

* User Profile Management
* Resume Processing
* Email Draft Generation
* Bulk Email Operations
* LinkedIn Automation
* Job Matching

## Future Enhancements

* Multi-user support
* Job board integrations
* Application tracking dashboard
* Resume optimization suggestions
* ATS score analysis
* AI-powered cover letter generation

## Author

Nikhil Rathod

Java Backend Developer
