package com.infiniteplay.accord.security.authentication;

import com.infiniteplay.accord.entities.AccountType;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class GithubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {


        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String username = (String) oAuth2User.getAttributes().get("login");
        int id = (int) oAuth2User.getAttributes().get("id");

        System.out.println("oauth2user attributes ID::" + id);
        //save user
        User user = userRepository.findByAccountIdAndAccountType(id, AccountType.GITHUB);
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());

        if(user != null) {
            attributes.put("login",user.getUsername() + "@" + user.getId());
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
        } else {
            //new user, registration required
            throw new OAuth2AuthenticationException(new OAuth2Error("302 Further Registration Required"), "302 " + username + " " + id);
        }


    }
}
