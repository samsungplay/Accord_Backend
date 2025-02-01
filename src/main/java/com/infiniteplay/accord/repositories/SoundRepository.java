package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.Sound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SoundRepository extends JpaRepository<Sound, Integer> {

    @Query(value = "DELETE FROM sound WHERE chatroom_id=?1",nativeQuery = true)
    @Modifying
    void deleteByChatroomId(Integer chatroomId);

    @Query(value="SELECT s FROM Sound s WHERE s.chatRoom IS NULL")
    List<Sound> findDefaultSounds();
}
