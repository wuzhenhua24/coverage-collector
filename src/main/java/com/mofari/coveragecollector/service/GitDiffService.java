package com.mofari.coveragecollector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitDiffService {

    private static final Logger logger = LoggerFactory.getLogger(GitDiffService.class);

    // Regex to find "+++ b/path/to/file.java"
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("^\\+\\+\\+\\s+b/(.*\\.java)");

    // Regex to find "@@ -old_start,old_lines +new_start,new_lines @@"
    // We only need to capture the new file's starting line number.
    // The ".*" at the end makes it robust against hunk headers that include
    // function context or other information after the "@@".
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@\\s+-[0-9]+(?:,[0-9]+)?\\s+\\+([0-9]+)(?:,([0-9]+))?\\s+@@.*");

    public Map<String, Set<Integer>> getChangedLines(String projectPath, String baseRef, String newRef) throws IOException, InterruptedException {
        Map<String, Set<Integer>> changedLinesMap = new HashMap<>();

        File workingDir = new File(projectPath);
        if (!workingDir.exists() || !workingDir.isDirectory()) {
            logger.error("Project path for git diff does not exist or is not a directory: {}", projectPath);
            throw new IOException("Invalid project path: " + projectPath);
        }

        // Command: git diff --unified=0 baseRef..newRef
        // --unified=0 shows only changed lines without context.
        // Removed '*.java' globbing to make it more robust across platforms.
        // The FILE_PATH_PATTERN already filters for .java files.
        ProcessBuilder processBuilder = new ProcessBuilder(
                "git", "diff", "--unified=0", baseRef + ".." + newRef
        );
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        logger.info("Executing git diff command in {}: {}", projectPath, String.join(" ", processBuilder.command()));

        Process process = processBuilder.start();

        String currentFile = null;
        int nextAddedLineNumber = 0; // The line number for the *next* line starting with '+'

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.trace("Git diff output: {}", line); // Use TRACE for verbose output
                Matcher fileMatcher = FILE_PATH_PATTERN.matcher(line);
                if (fileMatcher.matches()) {
                    currentFile = fileMatcher.group(1);
                    // Normalize path separators to match JaCoCo's typical format (Unix-style)
                    currentFile = currentFile.replace('\\', '/');
                    changedLinesMap.putIfAbsent(currentFile, new HashSet<>());
                    logger.debug("Processing diff for file: {}", currentFile);
                    continue; // Move to the next line
                }

                if (currentFile == null) {
                    // Skip any header lines until we find the first file (+++ b/...)
                    continue;
                }

                Matcher hunkMatcher = HUNK_HEADER_PATTERN.matcher(line);
                if (hunkMatcher.matches()) {
                    // This is a hunk header. It tells us where the additions start in the new file.
                    nextAddedLineNumber = Integer.parseInt(hunkMatcher.group(1));
                    continue; // We've got the line number, now process the actual added lines that follow.
                }

                // This is the most important part:
                // If a line starts with '+' and is not a '+++' file header, it's an added line of code.
                if (line.startsWith("+")) {
                    // It's an added line. Add the current line number to our set.
                    changedLinesMap.get(currentFile).add(nextAddedLineNumber);
                    // And increment the counter for the next added line.
                    nextAddedLineNumber++;
                }
                // We don't care about lines starting with '-' because they don't exist in the new version.
            }
        }

        int exitCode = process.waitFor();
        // For git diff, exit code 1 means differences were found, 0 means no differences.
        // Both are considered successful execution.
        if (exitCode != 0 && exitCode != 1) {
            logger.error("Git diff command failed with a critical exit code {}. Path: {}. Command: {}", exitCode, projectPath, String.join(" ", processBuilder.command()));
            throw new IOException("Git diff command failed with exit code " + exitCode + ". Check refs and project path.");
        } else {
            logger.info("Git diff command finished with exit code {} (0=no diffs, 1=diffs found).", exitCode);
        }

        logger.info("Found changes in {} files. Total changed lines tracked: {}", changedLinesMap.size(), changedLinesMap.values().stream().mapToLong(Set::size).sum());
        return changedLinesMap;
    }
}
