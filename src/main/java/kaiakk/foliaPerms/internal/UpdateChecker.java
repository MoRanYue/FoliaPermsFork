package kaiakk.foliaPerms.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronously checks for new plugin releases on GitHub.
 * Runs on a virtual thread so it does not block the server startup.
 */
public final class UpdateChecker {
    private static final String REPO_OWNER = "MoRanYue";
    private static final String REPO_NAME = "FoliaPermsFork";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/"
            + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String GITHUB_RELEASES_URL = "https://github.com/"
            + REPO_OWNER + "/" + REPO_NAME + "/releases";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private UpdateChecker() {}

    /**
     * Starts an asynchronous update check. If a newer version is found,
     * a warning message is printed to the console.
     *
     * @param currentVersion the currently installed plugin version (e.g. "0.1.1+26.1.2")
     * @param logger         the plugin logger
     */
    public static void check(String currentVersion, Logger logger) {
        Thread.startVirtualThread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_URL))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", "FoliaPermsFork/" + currentVersion)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.warning("Update check failed: GitHub API returned HTTP " + response.statusCode());
                    return;
                }

                // Extract "tag_name" from the JSON response using simple pattern matching.
                // The JSON structure is: { ..., "tag_name": "vX.Y.Z+...", ... }
                Pattern tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = tagPattern.matcher(response.body());
                if (!matcher.find()) {
                    logger.warning("Update check failed: unable to parse version from GitHub response.");
                    return;
                }

                String latestTag = matcher.group(1);
                String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

                if (isNewerVersion(latestVersion, currentVersion)) {
                    printUpdateMessage(logger, latestVersion, latestTag, currentVersion);
                }
            } catch (Exception e) {
                logger.warning("Update check failed: " + e.getMessage());
            }
        });
    }

    private static void printUpdateMessage(Logger logger, String latestVersion, String latestTag, String currentVersion) {
        String border = "*".repeat(55);
        logger.warning(border);
        logger.warning("*  " + centerText("New version available!", 51) + "  *");
        logger.warning("*" + " ".repeat(53) + "*");
        logger.warning("*  Latest:   " + padRight(latestVersion, 42) + "  *");
        logger.warning("*  Current:  " + padRight(currentVersion, 42) + "  *");
        logger.warning("*  Download: " + padRight(GITHUB_RELEASES_URL + "/tag/" + latestTag, 35) + "  *");
        logger.warning(border);
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s;
        return s + " ".repeat(len - s.length());
    }

    private static String centerText(String text, int width) {
        if (text.length() >= width) return text;
        int left = (width - text.length()) / 2;
        int right = width - text.length() - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    /**
     * Compares two version strings in MAJOR.MINOR.PATCH[+API] format.
     * Only the MAJOR.MINOR.PATCH part before '+' is compared.
     *
     * @return true if {@code latest} is strictly newer than {@code current}
     */
    static boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\+")[0].split("\\.");
        String[] currentParts = current.split("\\+")[0].split("\\.");

        int maxLen = Math.max(latestParts.length, currentParts.length);
        try {
            for (int i = 0; i < maxLen; i++) {
                int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }
}
