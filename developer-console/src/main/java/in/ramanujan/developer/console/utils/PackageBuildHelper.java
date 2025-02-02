package in.ramanujan.developer.console.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.developer.console.pojo.Constants;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.Dependency;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.PackageBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;

public class PackageBuildHelper {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static String readFile(String fileLocation) {
        try {
            String data = "";
            File file = new File(fileLocation);
            Scanner reader = new Scanner(file);
            while (reader.hasNextLine()) {
                data += reader.nextLine();
            }
            reader.close();
            return data;
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            System.exit(1);
            return null;
        }
    }

    public static String readFileWithNewLine(String fileLocation) {
        try {
            String data = "";
            File file = new File(fileLocation);
            Scanner reader = new Scanner(file);
            while (reader.hasNextLine()) {
                data += reader.nextLine();
                data += "\n";
            }
            reader.close();
            return data;
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            System.exit(1);
            return null;
        }
    }

    public static void addDependencies(List<Dependency> dependencies, PackageRunInput packageRunInput) {
        String ramanujanDependencyDir = Constants.ramanujanDependencyDir;
        if (!new File(ramanujanDependencyDir).exists()) {
            new File(ramanujanDependencyDir).mkdir();
        }
        File file;
        for(Dependency dependency : dependencies) {
            String dependencyDir = ramanujanDependencyDir + dependency.getGroupId() + "/" + dependency.getArtifactId() + "/"
                    + dependency.getVersion();
            file = new File(dependencyDir);
            if(file.exists()) {
                for(File dependencyFile : file.listFiles()) {
                    String fileLoc = dependencyDir + "/" + dependencyFile.getName();
                    packageRunInput.getHeaderCodes().put(fileLoc, readFile(fileLoc));
                }
            } else {
                //TODO: need to download from central repository. Exiting as of now
                System.out.println("Dependency not found: " + dependency);
            }
        }
    }

    public static void addOtherFilesInDirectory(String packageDirectory, String mainClass, PackageRunInput packageRunInput) {
        File[] files = new File(packageDirectory).listFiles();
        for(File file : files) {
            if(file.isDirectory()) {
                System.out.println("Directories are not allowed in the package");
                System.exit(1);
            }
            if(!file.isFile()) {
                System.out.println(file.getName()  + " is not a file");
                System.exit(1);
            }
            if(file.canExecute()) {
                System.out.println(file.getName() + " is executable. Not allowed");
                System.exit(1);
            }
            if("build.json".equals(file.getName())) {
                continue;
            }
            if(mainClass.equalsIgnoreCase(file.getName())) {
                continue;
            }
            packageRunInput.getHeaderCodes().put(file.getName(), readFile(packageDirectory + file.getName()));
        }
    }

    public static void addMainClassCode(String packageDirectory, String mainClass, PackageRunInput packageRunInput) {
        if(!new File(packageDirectory + mainClass).exists()) {
            System.out.println("MainClass " + mainClass + " doesn't exist");
            System.exit(1);
        }
        packageRunInput.setMainCode(readFile(packageDirectory + mainClass));
    }

    public static PackageBuilder getPackageBuilder(String packageDirectory) {
        String fileName = packageDirectory + "build.json";
        File file = new File(fileName);
        if(!file.exists()) {
            System.out.println("build.json doesn't exist");
            System.exit(1);
        }
        try {
            return objectMapper.readValue(readFile(fileName), PackageBuilder.class);
        } catch (Exception e) {
            System.out.println("Error in build.json. Could read it");
            System.exit(1);
        }
        return null;
    }
}
