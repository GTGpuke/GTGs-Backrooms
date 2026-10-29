package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rapport des tests du client au format JUnit, lu par la tache Gradle qui verifie le resultat ({@code verifyClientTests}).
 */
final class ClientTestReport {

    private final List<Result> results = new ArrayList<>();

    synchronized void passed(String name, long millis) {
        results.add(new Result(name, millis, null));
    }

    synchronized void failed(String name, long millis, String message, Throwable cause) {
        StringBuilder trace = new StringBuilder(message);
        for (Throwable link = cause; link != null; link = link.getCause()) {
            trace.append('\n').append(link);
            for (StackTraceElement element : link.getStackTrace()) {
                trace.append("\n    at ").append(element);
            }
        }
        results.add(new Result(name, millis, trace.toString()));
    }

    synchronized int failures() {
        int failures = 0;
        for (Result result : results) {
            if (result.failure != null) {
                failures++;
            }
        }
        return failures;
    }

    synchronized int size() {
        return results.size();
    }

    synchronized void write(Path file) throws IOException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"client\" tests=\"").append(results.size()).append("\" failures=\"").append(failures()).append("\">\n");
        for (Result result : results) {
            xml.append("  <testcase classname=\"client\" name=\"").append(escape(result.name)).append("\" time=\"")
                    .append(result.millis / 1000.0).append('"');
            if (result.failure == null) {
                xml.append("/>\n");
            } else {
                xml.append(">\n    <failure message=\"").append(escape(firstLine(result.failure))).append("\">")
                        .append(escape(result.failure)).append("</failure>\n  </testcase>\n");
            }
        }
        xml.append("</testsuite>\n");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, xml, StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    /** Texte sur dans un attribut ou un element XML : entites, et aucun caractere de controle (interdits en XML 1.0). */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c >= ' ' || c == '\n' || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private record Result(String name, long millis, String failure) {
    }
}
