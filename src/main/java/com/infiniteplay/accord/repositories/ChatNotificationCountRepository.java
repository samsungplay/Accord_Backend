package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatNotificationCount;
import com.infiniteplay.accord.entities.ChatReaction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatNotificationCountRepository extends CrudRepository<ChatNotificationCount,Integer> {


    List<ChatNotificationCount> findAllByUserId(Integer userId);

    ChatNotificationCount findByChatRoomIdAndUserId(Integer chatroomId, Integer userId);


    @Query("SELECT c FROM ChatNotificationCount c WHERE c.chatRoomId=:chatRoomId AND c.userId IN :userIds")
    List<ChatNotificationCount> findByChatRoomIdAndUserIds(@Param("chatRoomId") Integer chatRoomId,@Param("userIds") List<Integer> userIds);
    void deleteByChatRoomId(Integer chatroomId);

    void deleteByUserIdAndChatRoomId(Integer userId, Integer chatroomId);

}
