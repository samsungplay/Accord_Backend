package com.infiniteplay.accord.repositories;


import com.infiniteplay.accord.entities.AccountType;
import com.infiniteplay.accord.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {



    User findByEmail(String email);



    User findByAccountIdAndAccountType(Integer accountId, AccountType accountType);



    User findFirstByNicknameOrUsername(String nickname, String username);

    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    Set<User> findUsersByIds(@Param("ids") List<Integer> userIds);

    @Query(value = "SELECT * FROM accord_user WHERE (id = ?2 OR username =% ?1 OR nickname =% ?1) AND allow_non_friendsdm = TRUE LIMIT 50", nativeQuery = true)
    List<User> searchUser(String query, Integer id);



    @Query(value = "SELECT COUNT(*) > 0 FROM friends WHERE friends_id=?1 AND friend_of_id=?2",nativeQuery = true)
    boolean isFriend(Integer userId, Integer friendId);

    @Query(value = "SELECT COUNT(*) > 0 FROM blocked WHERE blocked_of_id=?1 AND blocked_id=?2", nativeQuery = true)
    boolean isBlocked(Integer blockerId, Integer blockedId);



}
