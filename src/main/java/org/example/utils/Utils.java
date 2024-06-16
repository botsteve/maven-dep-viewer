package org.example.utils;

import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.DepViewerException;
import org.example.model.DependencyNode;
import org.example.model.EnvSetting;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.example.utils.FxUtils.showError;

@Slf4j
public class Utils {

    public static final String DOWNLOADED_REPOS = "downloaded_repos";
    public static final String SETTINGS_FILE_PATH = "env-settings.properties";

    public static List<String> parseModulesFromPom(File pomFile) throws Exception {
        List<String> modules = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(pomFile);
        doc.getDocumentElement().normalize();

        NodeList nodeList = doc.getElementsByTagName("module");
        for (int i = 0; i < nodeList.getLength(); i++) {
            modules.add(nodeList.item(i).getTextContent());
        }

        return modules;
    }


    public static String getProjectName(File pomFile) throws Exception {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(pomFile);
        doc.getDocumentElement().normalize();

        NodeList nodeList = doc.getElementsByTagName("name");
        return nodeList.item(0).getTextContent();
    }

    public static String getPropertyFromSetting(String property) {
        var properties = loadSettings();
        return properties.getProperty(property, "");
    }

    public static Properties loadSettings() {
        Properties properties = new Properties();
        try (InputStream is = new FileInputStream(SETTINGS_FILE_PATH)) {
            properties.load(is);
        } catch (IOException e) {
            // Handle file not found or other errors
            log.error(e.getMessage());
            throw new DepViewerException(e);
        }
        return properties;
    }

    public static void saveSettings(ObservableList<EnvSetting> settingsList) {
        Properties properties = new Properties();
        settingsList.forEach(setting -> properties.setProperty(setting.getName(), setting.getValue()));

        try (OutputStream os = new FileOutputStream(SETTINGS_FILE_PATH)) {
            properties.store(os, "Environment Settings");
            log.info("Settings saved to {}", SETTINGS_FILE_PATH);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DepViewerException(e);
        }
    }

    public static boolean arePropertiesConfiguredAndValid() {
        var properties = Utils.loadSettings();
        var java8Home = properties.getProperty("JAVA8_HOME", "");
        var java11Home = properties.getProperty("JAVA11_HOME", "");
        var java17Home = properties.getProperty("JAVA17_HOME", "");
        if (java8Home.isEmpty() || java11Home.isEmpty() || java17Home.isEmpty()) {
            showError("""
                    Dependencies might be targeted for compilation with a different JDK version,
                    please configure JAVA8_HOME, JAVA11_HOME & JAVA17_HOME in settings -> environment settings.
                    """);
            return false;
        }

        if (isValidJdkHome(new File(java8Home))) {
            showError("""
                    JAVA8_HOME environment variable is not a valid JAVA_HOME.
                    JAVA8_HOME should point to the root directory path of the JDK.
                    """);
            return false;
        } else if (isValidJdkHome(new File(java11Home))) {
            showError("""
                    JAVA11_HOME environment variable is not a valid JAVA_HOME.
                    JAVA11_HOME should point to the root directory path of the JDK.
                    """);
            return false;
        } else if (isValidJdkHome(new File(java17Home))) {
            showError("""
                    JAVA17_HOME environment variable is not a valid JAVA_HOME.
                    JAVA17_HOME should point to the root directory path of the JDK.
                    """);
            return false;
        }
        return true;
    }

    public static void createSettingsFile() {

        // Check if the file exists
        File file = new File(SETTINGS_FILE_PATH);
        if (!file.exists()) {
            try {
                Properties properties = new Properties();
                file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    properties.store(fos, "Environment Settings");
                    log.info("Properties file created successfully.");
                } catch (IOException e) {
                    log.error(e.getMessage());
                    throw new DepViewerException(e);
                }
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new DepViewerException(e);
            }
        } else {
            log.info("Properties file already exists.");
        }
    }

    public static String retrieveSourceCompatibility(File projectDir, String jdkPath) throws IOException, InterruptedException {
        // Define the Gradle command
        String[] command = {"./gradlew", "properties", "-Porg.gradle.java.installations.auto-download=false"};

        // Start the process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("JAVA_HOME", jdkPath);
        Process process = processBuilder.start();

        // Read the output from the process
        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append("\n");
            }
        }

        // Wait for the process to complete
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new DepViewerException("Command execution failed with exit code: " + exitCode);
        }

        // Extract sourceCompatibility from the output
        String output = outputBuilder.toString();
        return extractGradleSourceCompatibility(output);
    }

    private static String extractGradleSourceCompatibility(String output) {
        // Define regular expression pattern to match sourceCompatibility
        Pattern pattern = Pattern.compile("sourceCompatibility:\\s*(\\S+)");
        Matcher matcher = pattern.matcher(output);

        // Find the first occurrence of sourceCompatibility
        if (matcher.find()) {
            return matcher.group(1); // Return the matched sourceCompatibility value
        } else {
            return null; // Return null if sourceCompatibility is not found
        }
    }

    public static Map<String, String> collectLatestVersions(Set<DependencyNode> urlVersions) {
        return urlVersions.stream()
                .collect(Collectors.toMap(
                        DependencyNode::getScmUrl, // Key is the URL
                        DependencyNode::getVersion, // Value is the version
                        (existingVersion, newVersion) -> compareVersions(existingVersion, newVersion) > 0 ? existingVersion
                                : newVersion
                ));
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    public static String getRepositoriesPath() throws URISyntaxException {
        String jarPath = Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();

        log.debug("Original Jarpath: {}", jarPath);

        // Adjust the path for Windows if it starts with a slash and contains a drive letter
        if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
            jarPath = jarPath.substring(1);
        }

        log.debug("Adjusted Jarpath for Windows: {}", jarPath);

        // Check if jarPath ends with .jar and adjust the path to point to the directory
        if (jarPath.endsWith(".jar")) {
            int lastSlashIndex = jarPath.lastIndexOf("/");
            if (lastSlashIndex != -1) {
                jarPath = jarPath.substring(0, lastSlashIndex + 1);
            }
        }

        log.debug("Trimmed Jarpath: {}", jarPath);

        // Convert the URI path to a platform-specific path
        Path jarDirPath = Paths.get(jarPath);

        // Ensure the path is absolute
        if (!jarDirPath.isAbsolute()) {
            jarDirPath = jarDirPath.toAbsolutePath();
        }

        log.debug("Absolute Path: {}", jarDirPath);

        // Manually concatenate the downloaded_repos directory
        Path combinedPath = jarDirPath.resolve(DOWNLOADED_REPOS);

        log.debug("Combined Path: {}", combinedPath);

        // Normalize the path to remove redundant elements
        Path finalPath = combinedPath.normalize();

        log.debug("Final Path: {}", finalPath);

        return finalPath.toString() + File.separator;
    }

    private static boolean isValidJdkHome(File jdkHome) {
        var binCheck = new File(jdkHome, "bin").isDirectory();
        var libCheck = new File(jdkHome, "lib").isDirectory();
        log.info("The bin folder for path {} exists? {}", jdkHome.getPath(), binCheck);
        log.info("The lib folder for path {} exists? {}", jdkHome.getPath(), libCheck);
        return !binCheck || !libCheck;
    }

    public static String concatenateRepoNames(String header, Set<String> names) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        sb.append(names.stream()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n")));
        return sb.toString();
    }
}
