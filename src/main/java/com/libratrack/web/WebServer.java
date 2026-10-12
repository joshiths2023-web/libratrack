package com.libratrack.web;

import com.libratrack.model.MembershipTier;
import com.libratrack.service.LibraryException;
import com.libratrack.service.LibraryService;
import com.libratrack.service.ReturnResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Web front end: a REST-style API over {@link LibraryService} plus the single-page UI. */
public class WebServer {

    private final LibraryService library;
    private final ApiMapper mapper;
    private final HttpServer server;

    public WebServer(LibraryService library, int port) throws IOException {
        this.library = library;
        this.mapper = new ApiMapper(library);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::serveIndex);
        server.createContext("/api/state", ex -> respond(ex, 200, Json.write(mapper.state())));
        server.createContext("/api/rfci", ex -> respond(ex, 200, Json.write(mapper.rfci())));
        action("/api/issue", this::issue);
        action("/api/return", this::returnBooks);
        action("/api/reserve", this::reserve);
        action("/api/cancel", this::cancel);
        action("/api/pay", this::pay);
        action("/api/member", this::registerMember);
        action("/api/clock", this::clock);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void action(String path, Function<Map<String, String>, String> handler) {
        server.createContext(path, ex -> {
            if (!"POST".equals(ex.getRequestMethod())) {
                respond(ex, 405, Json.write(Map.of("ok", false, "message", "POST required")));
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                body.put("message", handler.apply(form(ex)));
                body.put("ok", true);
            } catch (LibraryException | IllegalArgumentException e) {
                body.put("ok", false);
                body.put("message", e.getMessage());
            }
            respond(ex, 200, Json.write(body));
        });
    }

    private String issue(Map<String, String> f) {
        var tx = library.issueBook(num(f, "memberId"), num(f, "bookId"));
        return "Issued '" + library.book(tx.getBookId()).getTitle() + "' to " + library.member(tx.getMemberId()).getName()
                + ". Due " + tx.getDueDate();
    }

    private String returnBooks(Map<String, String> f) {
        List<Long> ids = new ArrayList<>();
        for (String part : f.getOrDefault("ids", "").split(",")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part.trim()));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ReturnResult r : library.returnBooks(ids)) {
            String title = library.book(r.transaction().getBookId()).getTitle();
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            if (r.fine() == null) {
                sb.append("Returned '").append(title).append("' - no fine");
            } else {
                sb.append(String.format(Locale.ROOT, "Returned '%s' - fine %.2f (%d overdue days; waivers: %s)", title,
                        r.fine().getNetAmount(), r.fine().getOverdueDays(), r.fine().getWaiverDetails()));
            }
        }
        return sb.toString();
    }

    private String reserve(Map<String, String> f) {
        var r = library.reserve(num(f, "memberId"), num(f, "bookId"));
        return "Reservation placed for '" + library.book(r.getBookId()).getTitle() + "'";
    }

    private String cancel(Map<String, String> f) {
        library.cancelReservation(num(f, "id"));
        return "Reservation cancelled";
    }

    private String pay(Map<String, String> f) {
        var fine = library.payFine(num(f, "id"));
        return String.format(Locale.ROOT, "Fine of %.2f paid", fine.getNetAmount());
    }

    private String registerMember(Map<String, String> f) {
        var m = library.registerMember(f.get("name"), MembershipTier.valueOf(f.getOrDefault("tier", "BASIC")));
        return "Registered " + m.getName() + " (" + m.getTier() + ")";
    }

    private String clock(Map<String, String> f) {
        if (f.containsKey("days")) {
            library.clock().advanceDays(Long.parseLong(f.get("days")));
        }
        if (f.containsKey("hours")) {
            library.clock().advanceHours(Long.parseLong(f.get("hours")));
        }
        if (f.containsKey("set")) {
            library.clock().set(LocalDateTime.parse(f.get("set")));
        }
        return "Clock is now " + library.clock().now();
    }

    private long num(Map<String, String> f, String key) {
        String v = f.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return Long.parseLong(v.trim());
    }

    private Map<String, String> form(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private void serveIndex(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            respond(ex, 404, "Not found");
            return;
        }
        try (InputStream page = WebServer.class.getResourceAsStream("/static/index.html")) {
            if (page == null) {
                respond(ex, 500, "index.html missing");
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", page.readAllBytes());
        }
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
