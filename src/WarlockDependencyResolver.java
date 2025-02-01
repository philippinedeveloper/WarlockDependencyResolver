import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;  // Add this import
import javax.xml.parsers.*;
import org.w3c.dom.*;
import javax.xml.transform.*;  // Add this import for Transformer
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import java.util.Scanner;

public class WarlockDependencyResolver {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String mainManifestPath = "AndroidManifest.xml";
        if (!Files.exists(Paths.get(mainManifestPath))) {
            System.out.println("No AndroidManifest.xml found in the current directory. Please ensure the file exists.");
            return;
        }

        System.out.println("Enter the number of JAR files you want to resolve:");
        int numJars = Integer.parseInt(scanner.nextLine());

        String[] jarPaths = new String[numJars];
        for (int i = 0; i < numJars; i++) {
            System.out.println("Enter the path for JAR file " + (i + 1) + ":");
            jarPaths[i] = scanner.nextLine();
        }

        try {
            resolveDependencies(mainManifestPath, jarPaths);
            System.out.println("Dependency resolution complete!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resolveDependencies(String mainManifestPath, String[] jarPaths) throws Exception {
        mergeAndroidManifests(mainManifestPath, jarPaths);
        mergeLibraries(jarPaths);
        mergeResources(jarPaths);
    }

    private static void mergeAndroidManifests(String mainManifestPath, String[] jarPaths) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document mainManifest = builder.parse(new File(mainManifestPath));

        for (String jarPath : jarPaths) {
            try (JarFile jarFile = new JarFile(jarPath)) {
                ZipEntry manifestEntry = jarFile.getEntry("AndroidManifest.xml");
                if (manifestEntry != null) {
                    InputStream input = jarFile.getInputStream(manifestEntry);
                    Document doc = builder.parse(input);
                    mergeXmlDocuments(mainManifest, doc);
                }
            }
        }

        writeXmlToFile(mainManifest, mainManifestPath);
        System.out.println("Merged AndroidManifest.xml");
    }

    private static void mergeLibraries(String[] jarPaths) throws IOException {
        String outputJarPath = "merged-output.jar";
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputJarPath))) {
            for (String jarPath : jarPaths) {
                try (JarFile jarFile = new JarFile(jarPath)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (!entry.isDirectory() && !entry.getName().startsWith("META-INF")) {
                            jarOut.putNextEntry(new JarEntry(entry.getName()));
                            try (InputStream in = jarFile.getInputStream(entry)) {
                                in.transferTo(jarOut);
                            }
                            jarOut.closeEntry();
                        }
                    }
                }
            }
        }
        System.out.println("Merged libraries into " + outputJarPath);
    }

    private static void mergeResources(String[] jarPaths) throws IOException {
        Path outputResourcesPath = Paths.get("merged-resources");
        Files.createDirectories(outputResourcesPath);

        for (String jarPath : jarPaths) {
            try (JarFile jarFile = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("res/")) {
                        Path outputPath = outputResourcesPath.resolve(entry.getName());
                        Files.createDirectories(outputPath.getParent());
                        try (InputStream in = jarFile.getInputStream(entry)) {
                            Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
        System.out.println("Merged resources into " + outputResourcesPath.toAbsolutePath());
    }

    private static void mergeXmlDocuments(Document base, Document toMerge) {
        NodeList childNodes = toMerge.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = base.importNode(childNodes.item(i), true);
            base.getDocumentElement().appendChild(node);
        }
    }

    private static void writeXmlToFile(Document doc, String filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");

        try (FileOutputStream output = new FileOutputStream(filePath)) {
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
                                  new javax.xml.transform.stream.StreamResult(output));
        }
    }
}
