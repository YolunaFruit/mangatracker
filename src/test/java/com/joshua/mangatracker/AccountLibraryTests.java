package com.joshua.mangatracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshua.mangatracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountLibraryTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private String csrf;

    private HttpResponse<String> send(String method, String path, String body, boolean withCsrf, boolean json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (withCsrf) request.header("X-CSRF-TOKEN", csrf);
        request.header("Content-Type", json ? "application/json" : "application/x-www-form-urlencoded");
        request.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }

    @Test void accountAndLibraryRoundTripRequiresSessionAndOwnershipAndPreservesManualProgress() throws Exception {
        csrf = json(send("GET", "/user/session", null, false, false)).path("csrfToken").asText();
        assertThat(send("POST", "/user/register", "username=reader&password=reading%26more", false, false).statusCode()).isEqualTo(403);
        var registered = send("POST", "/user/register", "username=reader&password=reading%26more", true, false);
        assertThat(registered.statusCode()).isEqualTo(200);
        assertThat(json(registered).has("password")).isFalse();
        assertThat(passwords.matches("reading&more", users.findByUsername("reader").getPassword())).isTrue();
        assertThat(send("POST", "/user/register", "username=reader&password=reading%26more", true, false).statusCode()).isEqualTo(409);
        assertThat(send("GET", "/progress/reader", null, false, false).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/user/login", "username=reader&password=wrong", true, false).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/user/login", "username=reader&password=reading%26more", true, false).statusCode()).isEqualTo(200);
        assertThat(json(send("GET", "/user/session", null, false, false)).path("user").path("username").asText()).isEqualTo("reader");
        assertThat(send("GET", "/progress/someone-else", null, false, false).statusCode()).isEqualTo(403);
        String id = "md:a1c7c817-4e59-43b7-9365-09675a149a6f";
        String entry = "{\"mangaId\":\"" + id + "\",\"title\":\"One Piece\",\"coverUrl\":\"javascript:alert(1)\"}";
        assertThat(send("POST", "/user/reader/add-manga", entry, true, true).statusCode()).isEqualTo(200);
        var added = send("POST", "/user/reader/add-manga", entry, true, true);
        assertThat(json(added).path("trackedManga").size()).isEqualTo(1);
        assertThat(json(added).path("trackedManga").get(0).path("coverUrl").isNull()).isTrue();
        var edited = send("POST", "/progress/reader/entry", "{\"mangaId\":\""+id+"\",\"chapter\":1100,\"status\":\"completed\",\"score\":9}", true, true);
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(json(edited).path("trackedManga").get(0).path("currentChapter").asInt()).isEqualTo(1100);
        var cleared = send("POST", "/progress/reader/entry", "{\"mangaId\":\""+id+"\",\"chapter\":1100,\"status\":\"reading\",\"score\":null}", true, true);
        assertThat(json(cleared).path("trackedManga").get(0).path("myScore").isNull()).isTrue();
        assertThat(send("POST", "/progress/reader/update?mangaId="+id+"&chapter=-2", null, true, false).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/progress/reader/status?mangaId="+id+"&status=invalid", null, true, false).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/progress/someone-else/remove?mangaId="+id, null, true, false).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/progress/reader/remove?mangaId="+id, null, true, false).statusCode()).isEqualTo(200);
        assertThat(json(send("GET", "/progress/reader", null, false, false)).path("trackedManga").isEmpty()).isTrue();
        assertThat(send("POST", "/user/logout", null, true, false).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/progress/reader", null, false, false).statusCode()).isEqualTo(401);
    }
}
