# Dependencies Viewer

A powerful JavaFX desktop application for analyzing **Maven** and **Gradle** project dependencies, resolving source code repositories, and rebuilding dependencies from source. Designed to help developers inspect dependency trees, fetch source code (Git), and compile projects using the correct JDK versions.

<img src="img/app.png" alt="Overview" width="800">

---

## Features

- **Multi-Build-System Support**: Analyze dependencies from both **Maven** (`pom.xml`) and **Gradle** (`build.gradle` / `build.gradle.kts`) projects.
- **Dependency Tree Visualization**: Graphically inspect the full dependency tree in a sortable, filterable tree-table.
- **Scope Column**: Each dependency displays its scope (e.g., `compile`, `runtime`, `test` for Maven; `implementation`, `api`, `compileOnly` for Gradle).
- **Scope Filter Dropdown**: Filter the dependency tree by scope using a dynamic dropdown populated from the loaded project.
- **Text Exclusion Filter**: Quickly exclude dependencies matching a text pattern.
- **Source Resolution**: Automatically resolves Git/SCM URLs for dependencies from Maven Central or Gradle module metadata.
- **Selective Download**: Choose specific dependencies and download their source code repositories.
- **Cross-Version Building**: Automatically builds dependencies using the correct JDK version (Java 8, 11, 17, 21) via Maven Toolchains or Gradle Wrapper.
- **Smart JDK Detection (Gradle)**: Reads the Gradle Wrapper version from `gradle-wrapper.properties` and selects the compatible JDK automatically — no manual configuration needed.
- **Verbose Build Logging**: All build tool invocations (Maven `-X`, Gradle `--info`, Ant `-verbose`) produce detailed output for troubleshooting.
- **Task Management**: Visual feedback with progress bars; prevents conflicting operations from running simultaneously.
- **Cross-Platform**: Runs on Windows, macOS, and Linux as a single executable JAR.

---

## 🚀 Getting Started

### Prerequisites

1. **Java 21+**: JDK 21 or higher is required to run the application itself.
   - *You do NOT need a special JavaFX-bundled JDK — the application includes JavaFX libraries.*
2. **Maven**: A local installation of Apache Maven (3.9.x recommended). Ensure `MAVEN_HOME` is set.
3. **Target JDKs**: To build older dependencies, install the relevant JDKs (e.g., JDK 8, JDK 11, JDK 17) and configure their paths in the app settings.
4. **Git**: Required for cloning dependency source code repositories.

### 🛠️ Installation

1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd maven-dep-searcher
   ```

2. **Build the Application**:
   ```bash
   mvn clean package
   ```

3. **Locate the JAR**:
   The executable file is generated at:
   ```
   target/maven-dep-searcher-1.0-SNAPSHOT.jar
   ```

---

## 🏃 Running the Application

```bash
java -jar target/maven-dep-searcher-1.0-SNAPSHOT.jar
```

> **Windows**: You may also double-click the JAR file if `.jar` is associated with Java 21.

---

## ⚙️ Configuration

### First-Time Setup: JDK Paths

1. Go to `Settings` → `Environment Settings`.
2. Use the **Browse** buttons to set paths for each JDK:
   - **JAVA8_HOME** — e.g., `/Library/Java/JavaVirtualMachines/jdk1.8.0_xxx.jdk/Contents/Home`
   - **JAVA11_HOME** — e.g., `/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
   - **JAVA17_HOME** — e.g., `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
   - **JAVA21_HOME** — e.g., `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`
3. Click **Save**. Settings are persisted in `env-settings.properties` in the application's working directory.

> **Required environment variables**: Ensure `JAVA_HOME` and `MAVEN_HOME` are set on your system. The application warns you if they are missing when opening a project.

### Smart JDK Detection for Gradle

When opening a Gradle project, the application automatically reads `gradle/wrapper/gradle-wrapper.properties` to determine the Gradle version, and selects the best-compatible JDK:

| Gradle Version | JDK Used       |
|----------------|----------------|
| 4.x            | `JAVA8_HOME`   |
| 5.x            | `JAVA11_HOME`  |
| 6.x            | `JAVA11_HOME`  |
| 7.0 – 7.5      | `JAVA17_HOME`  |
| 7.6+           | `JAVA17_HOME`  |
| 8.0 – 8.4      | `JAVA17_HOME`  |
| 8.5+           | `JAVA21_HOME`  |

If the detected JDK fails, the app automatically falls back to trying all other configured JDKs.

---

## 🌐 Proxy Configuration

If you are behind a corporate proxy, the application reads the standard **`http_proxy`** (or **`HTTP_PROXY`**) environment variable to configure outbound HTTP connections. This is used when downloading source code repositories (Git clone) from the internet.

### How to Configure

Set the `http_proxy` environment variable **before launching the application**:

#### macOS / Linux

```bash
export http_proxy=http://your-proxy-host:8080
java -jar target/maven-dep-searcher-1.0-SNAPSHOT.jar
```

Or add it permanently to your shell profile (`~/.zshrc`, `~/.bashrc`):

```bash
export http_proxy=http://your-proxy-host:8080
export https_proxy=http://your-proxy-host:8080
```

#### Windows (Command Prompt)

```cmd
set http_proxy=http://your-proxy-host:8080
java -jar target/maven-dep-searcher-1.0-SNAPSHOT.jar
```

#### Windows (PowerShell)

```powershell
$env:http_proxy = "http://your-proxy-host:8080"
java -jar target\maven-dep-searcher-1.0-SNAPSHOT.jar
```

### How it Works

When the application downloads source code (via the **Download Selected** button), it checks for the `http_proxy` environment variable. If present, it:

1. Parses the host and port from the URL (e.g., `http://proxy.example.com:8080`).
2. Configures a global Java `ProxySelector` that routes **all** HTTP/HTTPS connections through the proxy.
3. The proxy is used for Git clone operations and any network requests made by the application.

> **Note**: If `http_proxy` is not set, the application connects directly without a proxy. The scheme prefix (`http://`) is optional in the environment variable value — the app will add it automatically if missing.

### Maven Proxy

For Maven-specific proxy settings (used when resolving dependencies), configure your proxy in `~/.m2/settings.xml`:

```xml
<settings>
  <proxies>
    <proxy>
      <id>corporate-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>your-proxy-host</host>
      <port>8080</port>
      <!-- Optional: -->
      <username>proxyuser</username>
      <password>proxypass</password>
      <nonProxyHosts>localhost|*.internal.corp</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
```

### Gradle Proxy

For Gradle projects, configure proxy in `~/.gradle/gradle.properties`:

```properties
systemProp.http.proxyHost=your-proxy-host
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=your-proxy-host
systemProp.https.proxyPort=8080
# Optional:
# systemProp.http.proxyUser=proxyuser
# systemProp.http.proxyPassword=proxypass
# systemProp.http.nonProxyHosts=localhost|*.internal.corp
```

---

## 📖 Usage Guide

### 1. Open a Project

Click **Open Directory** and select the root directory of the project to analyze:
- **Maven**: Must contain a `pom.xml`
- **Gradle**: Must contain a `build.gradle` or `build.gradle.kts` (with or without a Gradle Wrapper)

The tool parses the project and displays the dependency tree with columns:

| Column       | Description                                        |
|--------------|----------------------------------------------------|
| ☑ Select     | Checkbox to select dependencies for download/build |
| Dependency   | `groupId:artifactId:version` tree hierarchy        |
| Scope        | Dependency scope (`compile`, `implementation`, etc) |
| SCM URL      | Resolved Git/source code repository URL            |
| Checkout Tag | Git tag to checkout for the dependency version     |
| Build With   | JDK version used to build this dependency          |

### 2. Filter & Select

- **Exclude filter**: Type in the "Exclude" text box to hide dependencies matching that text.
- **Scope filter**: Use the "Scope" dropdown to show only dependencies of a specific scope (e.g., only `compile` or only `test`).
- **Select All**: Check the "Select All" checkbox to select/deselect all visible dependencies.

### 3. Download Source

Click **Download Selected**. The tool clones the SCM repositories for the selected dependencies into the `downloaded_repos` folder (relative to the JAR location).

### 4. Build

Click **Build Selected**. The tool:
1. Inspects each downloaded project to detect the required Java version.
2. Generates a `toolchains.xml` (for Maven projects).
3. Triggers the build using the correct JDK:
   - **Maven**: `mvn clean install` with `-X` (verbose/debug output)
   - **Gradle**: `./gradlew clean build` with `--info` (verbose output)
   - **Ant**: `ant` with `-verbose` flag
4. Results are shown as a summary of successful and failed builds.

### 5. Context Menu

Right-click on any dependency to access additional options via the context menu.

---

## 🏗️ Architecture

```
com.botsteve.mavendepsearcher/
├── views/                  # JavaFX application views
│   ├── MainAppView.java        # Main application window
│   └── SettingsView.java       # Environment settings dialog
├── components/             # UI components
│   ├── TableViewComponent.java  # Tree-table + filter/scope controls
│   ├── ColumnsComponent.java    # Column definitions (scope, SCM, etc)
│   ├── ButtonsComponent.java    # Toolbar buttons
│   ├── CheckBoxComponent.java   # Select all checkbox
│   ├── ContextMenuComponent.java # Right-click menu
│   ├── MenuComponent.java       # Menu bar
│   └── ProgressBoxComponent.java # Progress bar
├── service/                # Business logic
│   ├── DependencyAnalyzerService.java      # Maven dependency analysis orchestrator
│   ├── DependencyTreeAnalyzerService.java  # Maven CLI tree parser
│   ├── GradleDependencyAnalyzerService.java # Gradle dependency analysis + JDK detection
│   ├── MavenInvokerService.java            # Maven Invoker API wrapper
│   ├── ScmUrlFetcherService.java           # Maven SCM URL resolver
│   └── GradleScmUrlFetcherService.java     # Gradle SCM URL resolver (Maven Central)
├── tasks/                  # Background tasks (JavaFX Task)
│   ├── DependencyLoadingTask.java    # Load dependency tree
│   ├── DependencyDownloaderTask.java # Download source repos
│   ├── BuildRepositoriesTask.java    # Build downloaded repos
│   └── ScmFetcherTask.java          # Fetch SCM URLs
├── model/                  # Data models
│   ├── DependencyNode.java          # Dependency with scope
│   ├── EnvSetting.java              # Settings key-value pair
│   └── CollectingOutputHandler.java # Maven output collector
├── utils/                  # Utilities
│   ├── Utils.java                   # Settings, version comparison
│   ├── FxUtils.java                 # JavaFX helpers
│   ├── ProxyUtil.java               # HTTP proxy configuration
│   ├── JavaVersionResolver.java     # JDK version resolution
│   └── ScmRepositories.java        # SCM URL normalization
├── exception/              # Custom exceptions
│   └── DepViewerException.java
└── logging/                # Logging configuration
    └── TextAreaAppender.java
```

---

## 🔧 Troubleshooting

| Issue | Solution |
|-------|---------|
| `java` or `mvn` not found | Ensure `JAVA_HOME` and `MAVEN_HOME` are in your system PATH |
| Gradle: "Unsupported class file major version" | The app auto-detects the compatible JDK from the Gradle Wrapper version. Ensure you have the appropriate JDK configured in Settings. |
| macOS M1/M2 build failures | Some older dependencies (e.g., Hibernate Validator with JRuby) may need an x86_64 JDK via Rosetta. |
| Proxy connection errors | Set the `http_proxy` environment variable before launching. See [Proxy Configuration](#-proxy-configuration). |
| No dependencies found | Ensure the project compiles. For Gradle, ensure `dependencies` configurations are declared. |
| Settings not saved | Check that `env-settings.properties` is writable in the JAR's directory. |

---

## 📋 Verbose Build Logging

All build tool commands run with verbose flags for detailed diagnostics:

| Build Tool | Flag         | Effect                                              |
|------------|--------------|-----------------------------------------------------|
| Maven      | `-X` (debug) | Full dependency resolution, plugin configs, POM      |
| Gradle     | `--info`     | Task execution, dependency resolution, build details |
| Ant        | `-verbose`   | Target/task execution, property resolution           |

All output is logged to the console at `INFO` level and visible in the application log.

---

## 📝 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

Developed by **Rusen Stefan @ Oracle**.
