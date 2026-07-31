package com.branz.mmorpg.persistence.migration;

import com.branz.mmorpg.api.result.Result;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClasspathMigrationCatalog {
    public static final String DEFAULT_INDEX = "db/migration/migrations.index";
    private static final Pattern FILE_NAME = Pattern.compile("V([0-9]+)__([a-z0-9_]+)\\.sql");

    private ClasspathMigrationCatalog() {}

    public static Result<MigrationCatalog, MigrationErrorCode> loadDefault() {
        return load(ClasspathMigrationCatalog.class.getClassLoader(), DEFAULT_INDEX);
    }

    static Result<MigrationCatalog, MigrationErrorCode> load(
            ClassLoader classLoader, String indexResource) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(indexResource, "indexResource");
        try {
            List<String> entries = readIndex(classLoader, indexResource);
            List<SqlMigration> migrations = new ArrayList<>();
            String directory = indexResource.substring(0, indexResource.lastIndexOf('/') + 1);
            for (String entry : entries) {
                Matcher matcher = FILE_NAME.matcher(entry);
                if (!matcher.matches()) {
                    return Result.failure(
                            MigrationErrorCode.MIGRATION_CATALOG_INVALID,
                            "Invalid migration file name: " + entry);
                }
                String resource = directory + entry;
                String sql = readResource(classLoader, resource);
                migrations.add(
                        SqlMigration.of(
                                Integer.parseInt(matcher.group(1)),
                                matcher.group(2).replace('_', ' '),
                                sql));
            }
            return MigrationCatalog.from(migrations);
        } catch (IOException | NumberFormatException exception) {
            return Result.failure(
                    MigrationErrorCode.MIGRATION_CATALOG_INVALID,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static List<String> readIndex(ClassLoader classLoader, String resource)
            throws IOException {
        String text = readResource(classLoader, resource);
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();
    }

    private static String readResource(ClassLoader classLoader, String resource)
            throws IOException {
        InputStream input = classLoader.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Classpath resource not found: " + resource);
        }
        try (input;
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        }
    }
}
