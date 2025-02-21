import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import java.util.Scanner;

public class WarlockDependencyResolver {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String mainManifestPath = "AndroidManifest.xml";

        System.out.println("Proceeding without checking for AndroidManifest.xml existence.");

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
        mergeLibrariesAndResources(jarPaths); 
    }

    private static void mergeAndroidManifests(String mainManifestPath, String[] jarPaths) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document mainManifest = null;

        Path manifestPath = Paths.get(mainManifestPath);
        if (Files.exists(manifestPath)) {
            mainManifest = builder.parse(new File(mainManifestPath));
        } else {
            mainManifest = builder.newDocument();
            Element root = mainManifest.createElement("manifest");
            mainManifest.appendChild(root);
        }

        for (String jarPath : jarPaths) {
            // Handle JAR or ZIP file
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                System.out.println("File does not exist: " + jarPath);
                continue;
            }

            try (JarFile jar = openJarFile(jarFile)) {
                ZipEntry manifestEntry = jar.getEntry("AndroidManifest.xml");
                if (manifestEntry != null) {
                    InputStream input = jar.getInputStream(manifestEntry);
                    Document doc = builder.parse(input);
                    mergeXmlDocuments(mainManifest, doc);
                }
            }
        }

        writeXmlToFile(mainManifest, mainManifestPath);
        System.out.println("Merged AndroidManifest.xml");
    }

    private static JarFile openJarFile(File file) throws IOException {
        if (file.getName().endsWith(".zip")) {
            return new JarFile(file);
        } else {
            return new JarFile(file);
        }
    }

    private static void mergeLibrariesAndResources(String[] jarPaths) throws IOException {
       String outputJarPath = "merged-output.jar";
       Set<String> classNames = new HashSet<>();

      try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputJarPath))) {
         for (String jarPath : jarPaths) {
             try (JarFile jarFile = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (entry.isDirectory() || entry.getName().startsWith("META-INF") || entry.getName().equals("AndroidManifest.xml")) {
                        continue;
                    }

                    if (entry.getName().endsWith(".class") && !classNames.contains(entry.getName())) {
                        classNames.add(entry.getName());
                        jarOut.putNextEntry(new JarEntry(entry.getName()));
                        try (InputStream in = jarFile.getInputStream(entry)) {
                            in.transferTo(jarOut);
                        }
                        jarOut.closeEntry();
                    }

                    if (entry.getName().startsWith("res/")) {
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
        System.out.println("Merged libraries and resources into " + outputJarPath);
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
            transformer.transform(new DOMSource(doc), new StreamResult(output));
        }
    }
}

