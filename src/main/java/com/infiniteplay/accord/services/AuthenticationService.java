package com.infiniteplay.accord.services;

import com.infiniteplay.accord.entities.AccountType;
import com.infiniteplay.accord.entities.ChatNotificationCount;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.OauthRegisterDetails;
import com.infiniteplay.accord.models.RegisterDetails;
import com.infiniteplay.accord.repositories.ChatNotificationCountRepository;
import com.infiniteplay.accord.repositories.UserRepository;
import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.utils.RegexConstants;
import com.infiniteplay.accord.utils.RegisterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatNotificationCountRepository chatNotificationCountRepository;

    @Autowired
    JWTHandler jwtHandler;
    @Value("${process.env}")
    String processEnv;

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie1 = ResponseCookie.from("accord_access_token","logout")
                .path("/")
                .sameSite(processEnv.equals("prod") ? "Lax" : "None")
                .secure(true)
                .httpOnly(true)
                .maxAge(0)
                .build();

        ResponseCookie cookie2 = ResponseCookie.from("accord_refresh_token","logout")
                .path("/")
                .sameSite(processEnv.equals("prod") ? "Lax" : "None")
                .secure(true)
                .httpOnly(true)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie1.toString());
        response.addHeader("Set-Cookie", cookie2.toString());
    }
    public static boolean isValidEmail(String email) {
        return Pattern.compile(RegexConstants.EMAIL_REGEX, Pattern.CASE_INSENSITIVE)
                .matcher(email)
                .matches();
    }

    public static boolean isValidUsername(String username) {
        return Pattern.compile(RegexConstants.USER_REGEX)
                .matcher(username).matches();
    }

    public static float computePasswordStrength(String password) {
        int conditionsSatisfied = 0;
        if (password.length() >= 7) {
            conditionsSatisfied++;
        }

        if (Pattern.compile(RegexConstants.PASSWORD_CONTAINS_SPECIAL_CHARACTER_REGEX).matcher(password).find()) {
            conditionsSatisfied++;
        }

        if (Pattern.compile(RegexConstants.PASSWORD_CONTAINS_DIGIT_REGEX).matcher(password).find()) {
            conditionsSatisfied++;
        }

        if(Pattern.compile(RegexConstants.PASSWORD_CONTAINS_LOWERCASE_REGEX).matcher(password).find()) {
            conditionsSatisfied++;
        }

        if (Pattern.compile(RegexConstants.PASSWORD_CONTAINS_UPPERCASE_REGEX).matcher(password).find()) {
            conditionsSatisfied++;
        }

        if(conditionsSatisfied == 1) return 0.1f;
        else if(conditionsSatisfied == 2) return 0.3f;
        else if(conditionsSatisfied == 3) return 0.5f;
        else if(conditionsSatisfied == 4) return 0.7f;

        return 1.0f;
    }


    @Transactional(readOnly = true)
    public boolean validateRegisterDetails(RegisterDetails registerDetails) throws RegisterException {

        if (registerDetails.getEmail().isEmpty() || registerDetails.getEmail().isBlank()) {
            throw new RegisterException("EMAIL", "Email is a required field");
        }

        if (registerDetails.getEmail().length() < 3 || registerDetails.getEmail().length() > 50) {
            throw new RegisterException("EMAIL", "Invalid Email Length");
        }

        if (!isValidEmail(registerDetails.getEmail())) {
            throw new RegisterException("EMAIL", "Invalid Email Format");
        }

        if (registerDetails.getNickname().length() > 0) {
            if (registerDetails.getNickname().length() < 2 || registerDetails.getNickname().length() > 30) {
                throw new RegisterException("NICKNAME", "Invalid Nickname Length");
            }
        }

        if (registerDetails.getUsername().isEmpty() || registerDetails.getUsername().isBlank()) {
            throw new RegisterException("USERNAME", "Username is a required field");
        }

        if (!isValidUsername(registerDetails.getUsername()) || registerDetails.getUsername().length() > 30 || registerDetails.getUsername().length() < 2) {
            throw new RegisterException("USERNAME", "Invalid Username");
        }


            if (registerDetails.getPassword().isEmpty() || registerDetails.getPassword().isBlank()) {
                throw new RegisterException("PASSWORD", "Password is a required field");
            }

            if (registerDetails.getPassword().length() < 3 || registerDetails.getPassword().length() > 25) {
                throw new RegisterException("PASSWORD", "Invalid Password Length");
            }

            if (computePasswordStrength(registerDetails.getPassword()) < 0.7) {
                throw new RegisterException("PASSWORD", "Password is too weak");
            }


        if (registerDetails.getBirthDate() == null) {
            throw new RegisterException("DATE", "Invalid Birthdate");
        }

        User userWithEmail = userRepository.findByEmail(registerDetails.getEmail());
        if (userWithEmail != null) {
            throw new RegisterException("EMAIL", "User with this email already exists");
        }


        return true;
    }

    @Transactional(readOnly = true)
    public boolean validateRegisterDetails(OauthRegisterDetails registerDetails) throws RegisterException {



        if (registerDetails.getEmail().isEmpty() || registerDetails.getEmail().isBlank()) {
            throw new RegisterException("EMAIL", "Email is a required field");
        }

        if (registerDetails.getEmail().length() < 3 || registerDetails.getEmail().length() > 25) {
            throw new RegisterException("EMAIL", "Invalid Email Length");
        }

        if (!isValidEmail(registerDetails.getEmail())) {
            throw new RegisterException("EMAIL", "Invalid Email Format");
        }

        if (registerDetails.getNickname().length() > 0) {
            if (registerDetails.getNickname().length() < 2 || registerDetails.getNickname().length() > 25) {
                throw new RegisterException("NICKNAME", "Invalid Nickname Length");
            }
        }

        if (registerDetails.getUsername().isEmpty() || registerDetails.getUsername().isBlank()) {
            throw new RegisterException("USERNAME", "Username is a required field");
        }

        if (!isValidUsername(registerDetails.getUsername()) || registerDetails.getUsername().length() > 30 || registerDetails.getUsername().length() < 2) {
            throw new RegisterException("USERNAME", "Invalid username");
        }



        if (registerDetails.getBirthDate() == null) {
            throw new RegisterException("DATE", "Invalid Birthdate");
        }

        User userWithEmail = userRepository.findByEmail(registerDetails.getEmail());
        if (userWithEmail != null) {
            throw new RegisterException("EMAIL", "User with this email already exists");
        }

        return true;
    }



    @Transactional
    public void registerGithub(OauthRegisterDetails registerDetails, String githubRegistrationToken) throws RegisterException {
        if(validateRegisterDetails(registerDetails)) {

            int accountId = jwtHandler.isValidGithubOauthToken(githubRegistrationToken);
            if(accountId == -1) {
                throw new RegisterException("Registration Token","Invalid registration token");
            }
            User user = new User(null, AccountType.GITHUB, registerDetails.getEmail(), registerDetails.getNickname(),
                    registerDetails.getUsername(),
                    null, registerDetails.getBirthDate());

            user.setAccountId(accountId);
            user.setRegisteredAt(Date.from(Instant.now()));

            user = userRepository.save(user);

            //initialize spam chat notification data
            ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
            chatNotificationCount.setChatRoomId(-1);
            chatNotificationCount.setUserId(user.getId());
            chatNotificationCount.setCount(0);
            chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
            chatNotificationCountRepository.save(chatNotificationCount);


        }
    }

    @Transactional
    public void register(RegisterDetails registerDetails) throws RegisterException {

        if(validateRegisterDetails(registerDetails)) {

            User user = new User(null, AccountType.ACCORD, registerDetails.getEmail(), registerDetails.getNickname(),
                    registerDetails.getUsername(),
                    passwordEncoder.encode(registerDetails.getPassword()), registerDetails.getBirthDate());

            user.setRegisteredAt(Date.from(Instant.now()));

            user = userRepository.save(user);
            //initialize spam chat notification data
            ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
            chatNotificationCount.setChatRoomId(-1);
            chatNotificationCount.setUserId(user.getId());
            chatNotificationCount.setCount(0);
            chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
            chatNotificationCountRepository.save(chatNotificationCount);
        }

    }









}
