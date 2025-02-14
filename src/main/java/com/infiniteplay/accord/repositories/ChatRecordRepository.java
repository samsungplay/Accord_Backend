package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRecord;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Integer>, CustomChatRecordRepository {


    @Query(value = "SELECT * FROM chat_record WHERE id=?1 AND sender_id=?2", nativeQuery = true)
    ChatRecord findByIdAndSenderId(Integer id, Integer senderId);

    @Modifying
    @Query(value = "DELETE FROM chat_record WHERE chatroom_id=?1", nativeQuery = true)
    void deleteChatRecords(Integer chatroomId);

    @Modifying
    @Query(value = "UPDATE chat_record SET reply_target_message=?1 WHERE reply_target_id=?2", nativeQuery = true)
    void updateReplyTargetMessage(String replyTargetMessage, Integer replyTargetId);



}
