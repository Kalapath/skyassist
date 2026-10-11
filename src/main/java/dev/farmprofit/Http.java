package dev.farmprofit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * One way to download data, for everything that reads an online source: runs in the background, reports to Health
 * (so failures are visible on the Status page with the reason), and never lets an error escape.
 */
final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Reads the body into the mod's data and returns a few words about it ("1,512 products"), or throws if the data is wrong. */
    interface Parser { String parse(String body) throws Exception; }

    static void get(String source, String url, int timeoutSeconds, Parser parser) { get(source, url, timeoutSeconds, Map.of(), parser, null); }

    static void get(String source, String url, int timeoutSeconds, Map<String, String> headers, Parser parser, Runnable onFail) {
        Health.attempt(source, url);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist")
                    .timeout(Duration.ofSeconds(timeoutSeconds)).GET();
            headers.forEach(b::header);
            CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).whenComplete((r, err) -> {
                try {
                    if (err != null) {
                        fail(source, onFail, reason(err));
                    } else if (r.statusCode() != 200) {
                        fail(source, onFail, status(r.statusCode()));
                    } else {
                        String detail = parser.parse(r.body());
                        Health.ok(source, detail);
                    }
                } catch (Throwable t) {
                    fail(source, onFail, "unreadable data: " + Safe.describe(t));
                }
            });
        } catch (Throwable t) {
            fail(source, onFail, reason(t));
        }
    }

    private static void fail(String source, Runnable onFail, String why) {
        Health.fail(source, why);
        if (onFail != null) {
            try { onFail.run(); } catch (Throwable t) { Safe.report("Retry " + source, t); }
        }
    }

    /** "HTTP 404 (not found ...)" */
    static String status(int code) {
        return "HTTP " + code + switch (code) {
            case 403 -> " (access denied, or a rate limit was hit)";
            case 404 -> " (not found - the address may have changed)";
            case 429 -> " (too many requests, will retry later)";
            default -> code >= 500 ? " (the server has a problem, will retry later)" : "";
        };
    }

    /** "connection timed out", "no internet (UnknownHostException: api.hypixel.net)" ... */
    static String reason(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String name = c.getClass().getSimpleName();
        String msg = c.getMessage() == null ? "" : c.getMessage();
        if (c instanceof java.net.http.HttpTimeoutException || c instanceof java.net.http.HttpConnectTimeoutException || name.contains("Timeout")) return "timed out";
        if (c instanceof java.net.UnknownHostException) return "can't find the server (no internet or DNS?): " + msg;
        if (c instanceof java.net.ConnectException) return "can't connect: " + msg;
        if (c instanceof javax.net.ssl.SSLException) return "secure connection failed: " + msg;
        return name + (msg.isEmpty() ? "" : ": " + msg);
    }

    private Http() {}
}
