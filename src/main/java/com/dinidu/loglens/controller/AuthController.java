package com.dinidu.loglens.controller;

import com.dinidu.loglens.dto.UserResponse;
import com.dinidu.loglens.security.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    @GetMapping("/user")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build();
        }

        UserResponse userResponse = UserResponse.fromUser(oauth2User.getUser());
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/status")
    public ResponseEntity<String> getAuthStatus(@AuthenticationPrincipal CustomOAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.ok("Not authenticated");
        }
        return ResponseEntity.ok("Authenticated as: " + oauth2User.getUser().getName());
    }
}
