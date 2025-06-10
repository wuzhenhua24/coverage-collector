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
    // To get \s, \d, \. to regex engine, Java string needs \\s, \\d, \\.
    // To get literal + to regex engine, Java string needs \\+
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("^\\+\\+\\+\\s+b/(.*\\.java)");
    // Regex to find "@@ -old_start,old_lines +new_start,new_lines @@"
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@\\s+-[0-9]+(?:,[0-9]+)?\\s+\\+([0-9]+)(?:,([0-9]+))?\\s+@@");

    public Map<String, Set<Integer>> getChangedLines(String projectPath, String baseRef, String newRef) throws IOException, InterruptedException {
        Map<String, Set<Integer>> changedLinesMap = new HashMap<>();
        
        File workingDir = new File(projectPath);
        if (!workingDir.exists() || !workingDir.isDirectory()) {
            logger.error("Project path for git diff does not exist or is not a directory: {}", projectPath);
            throw new IOException("Invalid project path: " + projectPath);
        }

        // Command: git diff --unified=0 baseRef..newRef -- '*.java'
        // --unified=0 shows only changed lines without context
        ProcessBuilder processBuilder = new ProcessBuilder(
                "git", "diff", "--unified=0", baseRef + ".." + newRef, "--", "*.java"
        );
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        logger.info("Executing git diff command in {}: {}", projectPath, String.join(" ", processBuilder.command()));

        Process process = processBuilder.start();
        
        String currentFile = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Git diff output: {}", line);
                Matcher fileMatcher = FILE_PATH_PATTERN.matcher(line);
                if (fileMatcher.matches()) {
                    currentFile = fileMatcher.group(1);
                    // Normalize path separators to match JaCoCo's typical format (Unix-style)
                    currentFile = currentFile.replace('\\', '/');
                    changedLinesMap.putIfAbsent(currentFile, new HashSet<>());
                    logger.debug("Processing diff for file: {}", currentFile);
                    continue;
                }

                if (currentFile == null) {
                    // Skip lines until a file path is found
                    continue;
                }
                
                Matcher hunkMatcher = HUNK_HEADER_PATTERN.matcher(line);
                if (hunkMatcher.matches()) {
                    int startLine = Integer.parseInt(hunkMatcher.group(1));
                    int lineCount = (hunkMatcher.group(2) != null) ? Integer.parseInt(hunkMatcher.group(2)) : 1;
                    // If lineCount is 0 for an added file's hunk, it means the file is new but empty, or only deletions happened.
                    // We are interested in lines *added* or *modified* in the newRef.
                    // The line numbers in the hunk header are 1-based.
                    // --unified=0 means the lines immediately following the hunk header are the added/modified lines.
                    // However, git diff output with --unified=0 can be tricky.
                    // A simpler way for --unified=0 is that any line starting with "+" that is not "+++ b/..." is an added line.
                    // The hunk tells us the *range* in the *new* file.
                    // Let's rely on lines starting with '+' after a hunk for added lines.
                    // The HUNK_HEADER_PATTERN gives us the starting line number in the *new* file.
                    // For simplicity with unified=0, we will parse lines starting with '+' *after* a hunk.
                    // The challenge with unified=0 is that it doesn't explicitly list line numbers for each '+' line.
                    // We need to count them from the startLine of the hunk.
                    // Let's refine: the hunk tells us the range [startLine, startLine + lineCount -1]. All these are "changed".
                    if (lineCount > 0) {
                        for (int i = 0; i < lineCount; i++) {
                            changedLinesMap.get(currentFile).add(startLine + i);
                        }
                        logger.debug("Added lines {}-{} to file {}", startLine, startLine + lineCount - 1, currentFile);
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // For git diff, exit code 1 means differences were found, 0 means no differences.
            // Other codes might mean an error.
            if (exitCode != 1 && changedLinesMap.isEmpty()) {
                 logger.error("Git diff command failed with exit code {}. Path: {}. Command: {}", exitCode, projectPath, String.join(" ", processBuilder.command()));
                 throw new IOException("Git diff command failed with exit code " + exitCode + ". Check refs and project path.");
            } else if (exitCode != 0 && exitCode != 1) {
                logger.warn("Git diff command exited with code {} (expected 0 or 1). Path: {}. Command: {}", exitCode, projectPath, String.join(" ", processBuilder.command()));
            } else {
                logger.info("Git diff command exited with code {} (0=no diffs, 1=diffs found).", exitCode);
            }
        }
        
        logger.info("Found changes in {} files. Total changed lines tracked: {}", changedLinesMap.size(), changedLinesMap.values().stream().mapToLong(Set::size).sum());
        return changedLinesMap;
    }
} 