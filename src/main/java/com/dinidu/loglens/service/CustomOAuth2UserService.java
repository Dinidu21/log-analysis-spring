package com.dinidu.loglens.service;


import com.dinidu.loglens.model.User;
import com.dinidu.loglens.repository.UserRepository;
import com.dinidu.loglens.security.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oauth2User);
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException("Error processing OAuth2 user");
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User.Provider provider = User.Provider.valueOf(registrationId.toUpperCase());

        String providerId;
        String email;
        String name;
        String avatarUrl;

        switch (provider) {
            case GOOGLE:
                providerId = oauth2User.getAttribute("sub");
                email = oauth2User.getAttribute("email");
                name = oauth2User.getAttribute("name");
                avatarUrl = oauth2User.getAttribute("picture");
                break;
            case GITHUB:
                providerId = oauth2User.getAttribute("id").toString();
                email = oauth2User.getAttribute("email");
                name = oauth2User.getAttribute("name");
                avatarUrl = oauth2User.getAttribute("avatar_url");

                // GitHub might not provide email in public scope
                if (email == null) {
                    email = oauth2User.getAttribute("login") + "@github.local";
                }
                if (name == null) {
                    name = oauth2User.getAttribute("login");
                }
                break;
            default:
                throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        }

        Optional<User> existingUser = userRepository.findByProviderId(providerId);
        if (existingUser.isEmpty()) {
            // Check if a user already exists with this email (e.g., signed up via Google)
            existingUser = userRepository.findByEmail(email);
        }

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Update existing user info
            user.setEmail(email);
            user.setName(name);
            user.setAvatarUrl(avatarUrl);
            user.setProvider(provider);
            user.setProviderId(providerId);
        } else {
            // Create new user
            user = User.builder()
                    .email(email)
                    .name(name)
                    .provider(provider)
                    .providerId(providerId)
                    .avatarUrl(avatarUrl)
                    .role(User.Role.USER)
                    .enabled(true)
                    .build();
        }

        user = userRepository.save(user);

        log.info("User processed: {} ({})", user.getName(), user.getProvider());

        return new CustomOAuth2User(oauth2User.getAttributes(), oauth2User.getName(), user);
    }
}
