package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.Background;
import com.infiniteplay.accord.entities.Sound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BackgroundRepository extends JpaRepository<Background, Integer> {

    @Query(value = "DELETE FROM background WHERE chatroom_id=?1",nativeQuery = true)
    @Modifying
    void deleteByChatroomId(Integer chatroomId);

    @Query(value="SELECT b FROM Background b WHERE b.chatRoom IS NULL")
    List<Background> findDefaultBackgrounds();
}
