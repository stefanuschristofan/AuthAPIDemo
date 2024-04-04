package com.christofan.test;

import com.christofan.test.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, String> userRequest, HttpServletResponse response) {
        if (!userRequest.containsKey("user_id") || !userRequest.containsKey("password")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "required user_id and password");
        }

        String userId = userRequest.get("user_id");
        if (userRepository.existsById(userId)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "already same user_id is used");
        }

        if (!checkLength(userId, 6, 20)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "invalid length for user_id");
        }
        if (!checkPattern(userId, "^[a-zA-Z0-9]*$")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "invalid character pattern for user_id");
        }

        String password = userRequest.get("password");
        if (!checkLength(password, 8, 20)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "invalid length for password");
        }
        if (!checkPattern(password, "[\\p{Alnum}\\p{Punct}]+")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return constructFailedMessage("Account creation failed", "invalid character pattern for password");
        }

        User user = new User();
        user.setUserId(userId);
        user.setPassword(password);
        user.setNickname(userId);
        userRepository.save(user);
        Map<String, String> userMap = Map.of("user_id", userId, "nickname", userId);
        return Map.of("message", "Account successfully created", "user", userMap);
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(
        @PathVariable String userId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Optional<User> authenticatedUser = authenticate(request);
        if (authenticatedUser.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("message", "Authentication Failed");
        }

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return Map.of("message", "No User found");
        }

        User user = userOptional.get();
        Map<String, String> userMap = new HashMap<>();
        userMap.put("user_id", user.getUserId());
        userMap.put("nickname", user.getNickname());
        if (user.getComment() != null) {
            userMap.put("comment", user.getComment());
        }
        return Map.of("message", "User details by user_id", "user", userMap);
    }

    @PatchMapping("/users/{userId}")
    public Map<String, Object> updateUser(
        @PathVariable String userId,
        @RequestBody Map<String, String> userRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Optional<User> authenticatedUserOptional = authenticate(request);
        if (authenticatedUserOptional.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("message", "Authentication Failed");
        }

        Optional<User> updatedUserOptional = userRepository.findById(userId);
        if (updatedUserOptional.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return Map.of("message", "No User found");
        }

        User authenticatedUser = authenticatedUserOptional.get();
        if (!userId.equals(authenticatedUser.getUserId())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return Map.of("message", "No Permission for Update");
        }

        if (userRequest.containsKey("user_id") || userRequest.containsKey("password")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return Map.of("message", "User updation failed", "cause", "not updatable user_id and password");
        }

        if (!userRequest.containsKey("nickname") && !userRequest.containsKey("comment")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return Map.of("message", "User updation failed", "cause", "required nickname or comment");
        }

        Map<String, String> userMap = new HashMap<>();
        User updatedUser = updatedUserOptional.get();
        if (userRequest.containsKey("nickname")) {
            String nickname = userRequest.get("nickname");
            if (!checkLength(nickname, 0, 30)) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return constructFailedMessage("User updation failed", "invalid length for nickname");
            }
            if (!checkPattern(nickname, "\\P{Cntrl}+")) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return constructFailedMessage("User updation failed", "invalid character pattern for nickname");
            }
            updatedUser.setNickname(nickname);
            userMap.put("nickname", nickname);
        }
        if (userRequest.containsKey("comment")) {
            String comment = userRequest.get("comment");
            if (!checkLength(comment, 0, 100)) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return constructFailedMessage("User updation failed", "invalid length for comment");
            }
            if (!checkPattern(comment, "\\P{Cntrl}+")) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return constructFailedMessage("User updation failed", "invalid character pattern for comment");
            }
            updatedUser.setComment(comment);
            userMap.put("comment", comment);
        }
        userRepository.save(updatedUser);

        return Map.of("message", "User successfully updated", "recipe", userMap);
    }

    @PostMapping("/close")
    public Map<String, Object> delete(HttpServletRequest request, HttpServletResponse response) {
        Optional<User> authenticatedUserOptional = authenticate(request);
        if (authenticatedUserOptional.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("message", "Authentication Failed");
        }

        User authenticatedUser = authenticatedUserOptional.get();
        userRepository.deleteById(authenticatedUser.getUserId());
        return Map.of("message", "Account and user successfully removed");
    }

    private Map<String, Object> constructFailedMessage(String message, String cause) {
        return Map.of("message", message, "cause", cause);
    }

    private boolean checkLength(String input, int minLength, int maxLength) {
        return input.length() >= minLength && input.length() <= maxLength;
    }

    private boolean checkPattern(String input, String pattern) {
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(input);
        return matcher.matches();
    }

    private Optional<User> authenticate(HttpServletRequest request) {
        String base64Authorization = request.getHeader("Authorization");
        if (base64Authorization == null) {
            return Optional.empty();
        }

        if (!base64Authorization.startsWith("Basic ")) {
            return Optional.empty();
        }

        byte[] authorizationBytes = Base64.getDecoder().decode(base64Authorization.substring(6).trim());
        String authorization = new String(authorizationBytes);
        String[] credentials = authorization.split(":");
        String userId = credentials[0];
        String password = credentials[1];
        return userRepository.findByUserIdAndPassword(userId, password);
    }
}
