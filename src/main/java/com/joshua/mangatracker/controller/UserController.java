package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final SecurityContextRepository contexts;

    public UserController(UserService userService, SecurityContextRepository contexts) {
        this.userService = userService;
        this.contexts = contexts;
    }

    @GetMapping("/session")
    public Map<String, Object> session(Principal principal, CsrfToken csrf) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", principal == null ? null : userService.findByUsername(principal.getName()));
        result.put("csrfToken", csrf.getToken());
        return result;
    }

    @PostMapping("/register")
    public User register(@RequestParam String username, @RequestParam String password) {
        User user = userService.register(username, password);
        if (user == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        return user;
    }

    @PostMapping("/login")
    public User login(@RequestParam String username, @RequestParam String password,
                      HttpServletRequest request, HttpServletResponse response) {
        User user = userService.login(username, password);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        request.getSession();
        request.changeSessionId();
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return user;
    }

    @PostMapping("/{username}/add-manga")
    @PreAuthorize("#username == authentication.name")
    public User addManga(@PathVariable String username, @RequestBody AddMangaRequest req) {
        User user = userService.findByUsername(username);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        return userService.addMangaBasic(user.getId(), req.mangaId, req.title, req.coverUrl);
    }

    public static class AddMangaRequest {
        public String mangaId;
        public String title;
        public String coverUrl;
    }
}
