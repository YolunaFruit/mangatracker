package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@CrossOrigin
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam String username, @RequestParam String password) {
        try {
            User created = userService.register(username, password);
            if (created == null) return ResponseEntity.badRequest().body("Username already exists.");
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error during register: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        try {
            User u = userService.login(username, password);
            if (u == null) return ResponseEntity.status(401).body("Invalid username or password.");
            return ResponseEntity.ok(u);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error during login: " + e.getMessage());
        }
    }

    @PostMapping("/{username}/add-manga")
    public ResponseEntity<?> addManga(@PathVariable String username, @RequestBody AddMangaRequest req) {
        try {
            User u = userService.findByUsername(username);
            if (u == null) return ResponseEntity.badRequest().body("User not found: " + username);

            User updated = userService.addMangaBasic(u.getId(), req.mangaId, req.title, req.coverUrl);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public static class AddMangaRequest {
        public String mangaId;
        public String title;
        public String coverUrl;
    }

}
