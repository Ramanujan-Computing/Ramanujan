package in.ramanujan.developer.console.operationImpl;

import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.developer.console.pojo.Constants;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.Dependency;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.PackageBuilder;
import in.ramanujan.developer.console.utils.PackageBuildHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/*
 * Required arguments: executePackage packageDirectory
 * packageDirectory will have all the files
 * There will be build.json which will contain external dependency that is required.
 * ~/.ramanujan/dependencies/ to have the dependencies
 * If dependency is there in the directory, it will be included from that directory. If directory is not there, it will be
 * downloaded (this feasibility has to be implemented)
 * */
public class Builder implements Operation {
    @Override
    public void execute(List<String> args) {
        String packageDirectory = args.get(0);
        PackageBuilder packageBuilder = PackageBuildHelper.getPackageBuilder(packageDirectory);
        String mainClass = packageBuilder.getMainClass();
        List<Dependency> dependencies = packageBuilder.getDependencies();
        PackageRunInput packageRunInput = new PackageRunInput();
        PackageBuildHelper.addMainClassCode(packageDirectory, mainClass, packageRunInput);
        PackageBuildHelper.addOtherFilesInDirectory(packageDirectory, mainClass, packageRunInput);
        PackageBuildHelper.addDependencies(dependencies, packageRunInput);

        storeInDependency(packageRunInput, packageBuilder);
    }

    private void storeInDependency(PackageRunInput packageRunInput, PackageBuilder packageBuilder) {
        File machineDepenedencyDir = new File(Constants.ramanujanDependencyDir);
        if(!machineDepenedencyDir.exists() || !machineDepenedencyDir.isDirectory()) {
            machineDepenedencyDir.mkdir();
        }
        createIfRequiredDirectoryNotPresent(packageBuilder);
        String dependencyDir = Constants.ramanujanDependencyDir + packageBuilder.getGroupId() + "/" + packageBuilder.getArtifactId() + "/"
                + packageBuilder.getVersion();
        try {
            FileWriter fWriter = new FileWriter(dependencyDir + "/" + packageBuilder.getMainClass());
            fWriter.write(packageRunInput.getMainCode());
            fWriter.close();

            for (String dependencyFileName : packageRunInput.getHeaderCodes().keySet()) {
                fWriter = new FileWriter(dependencyDir + "/" + dependencyFileName);
                fWriter.write(packageRunInput.getHeaderCodes().get(dependencyFileName));
                fWriter.close();
            }
        } catch (IOException e) {
            System.out.println("Something wrong with FileSystem");
            System.exit(1);
        }

        storeInServerRepository(packageBuilder, packageRunInput);
        System.out.println(new File(dependencyDir).getAbsolutePath());
    }

    private void createIfRequiredDirectoryNotPresent(PackageBuilder packageBuilder) {
        String dirPath = Constants.ramanujanDependencyDir;

        dirPath += "/" + packageBuilder.getGroupId();

        dirPath += "/" + packageBuilder.getArtifactId();

        File file = new File(dirPath + "/" + packageBuilder.getVersion());
        if(!file.exists() || !file.isDirectory()) {
            file.mkdirs();
        }
    }

    private void storeInServerRepository(PackageBuilder packageBuilder, PackageRunInput packageRunInput) {
        //TODO: Need to store the dependency onto server repository
    }

}
