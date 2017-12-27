/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.project;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import static org.nmrfx.processor.project.ProjectLoader.currentProjectDir;
import static org.nmrfx.processor.project.ProjectLoader.getIndex;

/**
 *
 * @author Bruce Johnson
 */
public class GUIProjectLoader extends ProjectLoader {

    public void loadGUIProject(Path projectDir) throws IOException, IllegalStateException {
        loadProject(projectDir);
        FileSystem fileSystem = FileSystems.getDefault();

        String[] subDirTypes = {"windows"};
        if (projectDir != null) {
            for (String subDir : subDirTypes) {
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "windows":
                            loadWindows(subDirectory);
                            break;
                        default:
                            throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
        currentProjectDir = projectDir;
    }

    public void saveProject() throws IOException {
        if (currentProjectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        super.saveProject();
        saveWindows();
    }

    void loadWindows(Path directory) throws  IOException {
        Pattern pattern = Pattern.compile("(.+)\\.(txt|ppm)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    sorted(new ProjectLoader.FileComparator()).
                    forEach(path -> {
                        String fileName = path.getFileName().toString();
                        Optional<Integer> fileNum = getIndex(fileName);
                        int ppmSet = fileNum.isPresent() ? fileNum.get() : 0;
                    });
        }
    }

    void saveWindows() throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();

        if (currentProjectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path projectDir = currentProjectDir;
        int ppmSet = 0;
        String fileName = String.valueOf(ppmSet) + "_" + "ppm.txt";
        String subDir = "windows";
        Path peakFilePath = fileSystem.getPath(projectDir.toString(), subDir, fileName);
        // fixme should only write if file doesn't already exist or peaklist changed since read
        try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
            writer.close();
        } catch (IOException ioE) {
            throw ioE;
        }
    }

}
