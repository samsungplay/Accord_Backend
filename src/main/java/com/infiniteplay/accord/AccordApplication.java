package com.infiniteplay.accord;

import com.infiniteplay.accord.entities.AccountType;
import com.infiniteplay.accord.entities.Background;
import com.infiniteplay.accord.entities.Sound;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.ChatMessage;
import com.infiniteplay.accord.models.RegisterDetails;
import com.infiniteplay.accord.repositories.BackgroundRepository;
import com.infiniteplay.accord.repositories.ChatRecordRepository;
import com.infiniteplay.accord.repositories.SoundRepository;
import com.infiniteplay.accord.repositories.UserRepository;
import com.infiniteplay.accord.services.*;
import com.infiniteplay.accord.utils.TimeUtils;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SpringBootApplication
@Slf4j
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
public class AccordApplication {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    ChatRoomService chatRoomService;

    @Autowired
    ChatService chatService;

    @Value("${process.env}")
    private String processEnv;

    @Autowired
    AuthenticationService authenticationService;


    public static void main(String[] args) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        SpringApplication.run(AccordApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(PasswordEncoder passwordEncoder, ChatRecordRepository chatRecordRepository, JanusService janusService, SoundRepository soundRepository, BackgroundRepository backgroundRepository) {


        return (args) -> {


//			log.info("JWT secret: " + Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()));


            if (processEnv.equals("dev")) {
                authenticationService.register(new RegisterDetails("samsungplayee@gmail.com", "Pjkm38597071!!", "samsungplay", "samsungplayer1", Date.from(Instant.now())));
                authenticationService.register(new RegisterDetails("samsungplayee2@gmail.com", "Pjkm38597071!!", "samsungplay", "samsungplayer2", Date.from(Instant.now())));
                authenticationService.register(new RegisterDetails("samsungplayee3@gmail.com", "Pjkm38597071!!", "samsungplay", "samsungplayer3", Date.from(Instant.now())));
                authenticationService.register(new RegisterDetails("samsungplayee4@gmail.com", "Pjkm38597071!!", "samsungplay", "samsungplayer4", Date.from(Instant.now())));
                authenticationService.register(new RegisterDetails("samsungplayer@gmail.com", "Pjkm38597071!!", "소리", "평화", Date.from(Instant.now())));

                userService.sendFriendRequest("samsungplay@1", "samsungplay@3");
                userService.acceptFriendRequest("samsungplay@3", "samsungplay@1");

                userService.sendFriendRequest("samsungplay@1", "samsungplay@4");
                userService.acceptFriendRequest("samsungplay@4", "samsungplay@1");

                userService.sendFriendRequest("samsungplay@3", "samsungplay@4");
                userService.acceptFriendRequest("samsungplay@4", "samsungplay@3");

                userService.sendFriendRequest("소리@5", "samsungplay@4");
                userService.acceptFriendRequest("samsungplay@4", "소리@5");
                userService.sendFriendRequest("소리@5", "samsungplay@3");
                userService.acceptFriendRequest("samsungplay@3", "소리@5");


                chatRoomService.createDirectMessagingChatroom("소리@5", List.of("samsungplay@4"), "DM", true);
                chatRoomService.createDirectMessagingChatroom("소리@5", List.of("samsungplay@4", "samsungplay@3"), "A room", false);
            }

            //seed default sounds to the database
            List<Sound> sounds = soundRepository.findDefaultSounds();
            if (sounds.isEmpty()) {
                Sound sound = new Sound(null, null, "sound", "Chirp", ":bird:", "sound_chirp.wav", 3210);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Explosion", ":boom:", "sound_explosion.wav", 4865);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Wind", ":dash:", "sound_wind.mp3", 2090);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Bell", ":bell:", "sound_bell.wav", 889);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Splash", ":droplet:", "sound_splash.mp3", 2417);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Car", ":car:", "sound_car.wav", 5000);
                soundRepository.save(sound);
                sound = new Sound(null, null, "sound", "Rain", ":rain_cloud:", "sound_rain.wav", 2415);
                soundRepository.save(sound);
            }

            //seed default backgrounds to the database
            List<Background> backgrounds = backgroundRepository.findDefaultBackgrounds();
            if (backgrounds.isEmpty()) {
                Background bg = new Background(null, null, "Cafe", "background_cafe.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "City", "background_city.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Cafe", "background_cafe.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Forest", "background_forest.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Future", "background_future.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Galaxy", "background_galaxy.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Gradient", "background_gradient.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Living Room", "background_livingroom.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Mountain", "background_mountain.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Tropical", "background_tropical.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Woodland", "background_woodland.jpg");
                backgroundRepository.save(bg);
                bg = new Background(null, null, "Workspace", "background_workspace.jpg");
                backgroundRepository.save(bg);
            }


//			LocalDateTime startPoint = LocalDateTime.of(2024,10,21,0,0,0).minusDays(500);
//			ZonedDateTime date = ZonedDateTime.of(startPoint, TimeUtils.KST_ZONE);
//			for(int i=0; i<100; i++) {
//				chatService.sendMessage("소리@5", "1", new ChatMessage("This message was sent in #!@$^" + startPoint.format(DateTimeFormatter.ISO_LOCAL_DATE),null,null,null),null,null,
//						date);
//				chatService.sendMessage("소리@5", "1", new ChatMessage("Another message was sent in #!@$^" + startPoint.format(DateTimeFormatter.ISO_LOCAL_DATE),null,null,null),null,null,
//						date);
//				startPoint = startPoint.plusDays(1);
//				date = ZonedDateTime.of(startPoint, TimeUtils.KST_ZONE);
//
//			}

//			System.out.println(chatRecordRepository.findAll());
//			user1 = userRepository.findByIdAndUsername(1,"samsungplay");

//			user2 = userRepository.findByUsername("samsungplay2");
//			user3 = userRepository.findByIdAndUsername(3,"samsungplay");
//			user1.getBlocked().add(user3);
//			userRepository.save(user1);
//
//			Set<User> friends = new HashSet<>();
//
//			friends.add(user2);
////			friends.add(user3);
//
//			user1.setFriends(friends);
//
//			log.info("saving friends");
//
//			userRepository.save(user1);
//
//			log.info("querying a user.");
//
//			userRepository.findByUsername("samsungplay");

//			janusService.createConnection(1);
//			janusService.createRoom(1,10);
//			janusService.joinRoomAsPublisher(1,10);
//			janusService.joinRoomAsSubscriber(1,10);
//			janusService.destroyConnection(1);


            log.info("Server initialized");
        };
    }


}
