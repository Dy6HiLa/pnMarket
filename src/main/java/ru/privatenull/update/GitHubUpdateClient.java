package ru.privatenull.update;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateClient {
    private static final Pattern VERSION = Pattern.compile(
            "v?\\d+(?:\\.\\d+){0,3}(?:[-+][A-Za-z0-9._-]+)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_VERSION = Pattern.compile(
            "\"(?:version|latestVersion|latest_version|tag_name|name)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_DOWNLOAD = Pattern.compile(
            "\"(?:downloadUrl|download_url|html_url)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAR_DOWNLOAD = Pattern.compile(
            "\"browser_download_url\"\\s*:\\s*\"([^\"]+?\\.jar[^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern YAML_VERSION = Pattern.compile(
            "(?m)^\\s*version\\s*:\\s*['\"]?([^'\"\\r\\n]+)['\"]?"
    );

    private final String repository;
    private final String downloadUrl;

    GitHubUpdateClient(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Некорректный GitHub repository");
        }
        this.repository = repository;
        this.downloadUrl = "https://github.com/" + repository + "/releases/latest";
    }

    UpdateInfo fetchLatest() throws Exception {
        List<UpdateInfo> candidates = new ArrayList<>();
        Exception lastFailure = null;
        for (Source source : sources()) {
            try {
                String body = fetch(source.url());
                UpdateInfo info = source.yaml() ? fromPluginYaml(body) : fromJson(body);
                if (info.version() != null && !info.version().isBlank()) candidates.add(info);
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }
        UpdateInfo newest = null;
        for (UpdateInfo candidate : candidates) {
            if (newest == null || VersionComparator.compare(candidate.version(), newest.version()) > 0) {
                newest = candidate;
            }
        }
        if (newest != null) return newest;
        if (lastFailure != null) throw new IllegalStateException("Все источники GitHub недоступны", lastFailure);
        return new UpdateInfo(null, downloadUrl);
    }

    private List<Source> sources() {
        String raw = "https://raw.githubusercontent.com/" + repository + "/";
        String api = "https://api.github.com/repos/" + repository + "/";
        return List.of(
                new Source(raw + "main/update-manifest.json", false),
                new Source(raw + "master/update-manifest.json", false),
                new Source(api + "releases/latest", false),
                new Source(api + "tags", false),
                new Source(raw + "main/src/main/resources/plugin.yml", true),
                new Source(raw + "master/src/main/resources/plugin.yml", true)
        );
    }

    private UpdateInfo fromJson(String body) {
        String version = extract(JSON_VERSION, body);
        String download = extract(JAR_DOWNLOAD, body);
        if (download == null) download = extract(JSON_DOWNLOAD, body);
        return new UpdateInfo(cleanVersion(version), download == null ? downloadUrl : unescape(download));
    }

    private UpdateInfo fromPluginYaml(String body) {
        return new UpdateInfo(cleanVersion(extract(YAML_VERSION, body)), downloadUrl);
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "pnMarket UpdateChecker");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
                return body.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private String extract(Pattern pattern, String body) {
        if (body == null) return null;
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanVersion(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION.matcher(value.trim());
        return matcher.find() ? matcher.group() : null;
    }

    private String unescape(String value) {
        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private record Source(String url, boolean yaml) {
    }
}
