package com.mofari.coveragecollector.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SourceClassFinder {

    // 你可以像我之前那样把这些定义为常量，也可以根据需要传入
    // private static final String SOURCE_PATH_SUFFIX = "src/main/java";
    // private static final String CLASSES_PATH_SUFFIX = "target/classes";

    /**
     * 递归查找指定根目录下的所有符合目标模式的目录。
     *
     * @param rootDir       查找的起始根目录
     * @param targetPattern 要查找的目录模式，例如 "src/main/java" 或 "target/classes"
     * @return 找到的符合模式的目录的绝对路径列表
     */
    public static List<String> findDirectories(File rootDir, String targetPattern) {
        List<String> foundPaths = new ArrayList<>();
        Path rootPath = rootDir.toPath();

        if (!Files.isDirectory(rootPath)) {
            System.err.println("Error: Provided path is not a directory: " + rootDir.getAbsolutePath());
            return foundPaths; // 返回空列表
        }

        // 确保目标模式使用统一的分隔符，处理跨平台兼容性
        String normalizedTargetPattern = targetPattern.replace(File.separatorChar, '/');

        try (Stream<Path> walk = Files.walk(rootPath)) {
            walk.forEach(path -> {
                // 只处理目录
                if (Files.isDirectory(path)) {
                    // 获取当前目录相对于根目录的相对路径
                    String relativePath = rootPath.relativize(path).toString();

                    // 同样，确保相对路径使用统一的分隔符进行比较
                    if (File.separatorChar == '\\') {
                        relativePath = relativePath.replace('\\', '/');
                    }

                    // 判断相对路径是否以目标模式结尾
                    if (relativePath.endsWith(normalizedTargetPattern)) {
                        foundPaths.add(path.toAbsolutePath().toString());
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            e.printStackTrace();
        }
        return foundPaths;
    }

    public static void main(String[] args) {
        // 示例用法
        String appBaseDir = "C:\\Users\\youruser\\Documents\\my_projects"; // **请替换为你的实际根目录路径**

        SourceClassFinder finder = new SourceClassFinder();

        // 查找所有 src/main/java 目录
        List<String> javaSourceDirs = finder.findDirectories(new File(appBaseDir), "src/main/java");
        System.out.println("--- Found src/main/java Directories ---");
        if (javaSourceDirs.isEmpty()) {
            System.out.println("No src/main/java directories found under: " + appBaseDir);
        } else {
            javaSourceDirs.forEach(System.out::println);
        }

        System.out.println("\n"); // 添加空行分隔

        // 查找所有 target/classes 目录
        List<String> javaClassDirs = finder.findDirectories(new File(appBaseDir), "target/classes");
        System.out.println("--- Found target/classes Directories ---");
        if (javaClassDirs.isEmpty()) {
            System.out.println("No target/classes directories found under: " + appBaseDir);
        } else {
            javaClassDirs.forEach(System.out::println);
        }
    }
}