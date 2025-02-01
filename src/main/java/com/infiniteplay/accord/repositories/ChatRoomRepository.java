package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRecord;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import org.hibernate.query.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ChatRoomRepository extends CrudRepository<ChatRoom, Integer> {

    ChatRoom findByDirect1to1Identifier(String direct1to1Identifier);


    @Query(value = "SELECT COUNT(*) > 0 FROM chatrooms WHERE chatrooms_participant_id=?1 AND chatrooms_id=?2",nativeQuery = true)
    boolean isChatRoomOf(Integer userId, Integer chatroomId);

    @Modifying
    @Query(value = "DELETE FROM chatrooms WHERE chatrooms_id=?1", nativeQuery = true)
    void deleteChatRoomAssociation(Integer chatroomId);

    @Query(value = "SELECT * FROM accord_chatroom WHERE (id=?2 OR name =% ?1) AND is_public = TRUE AND direct1to1identifier IS NULL LIMIT 50", nativeQuery = true)
    List<ChatRoom> searchChatRoomByName(String query, Integer id);


}
