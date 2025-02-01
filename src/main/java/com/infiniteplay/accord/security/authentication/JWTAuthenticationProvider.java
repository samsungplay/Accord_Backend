package com.infiniteplay.accord.security.authentication;

import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.repositories.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;


@RequiredArgsConstructor
@Component
public class JWTAuthenticationProvider implements AuthenticationProvider {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String userInputPassword = authentication.getCredentials().toString();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new BadCredentialsException("Invalid email");
        }


        if (passwordEncoder.matches(userInputPassword, user.getPassword())) {

            return new UsernamePasswordAuthenticationToken(user.getUsername() + "@" + user.getId(), user.getPassword(), List.of(new SimpleGrantedAuthority("ROLE_USER")));
        }
        else {
            throw new BadCredentialsException("Incorrect password");
        }

    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
