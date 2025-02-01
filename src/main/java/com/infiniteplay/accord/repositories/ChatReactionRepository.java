package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatReaction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

    public interface ChatReactionRepository extends CrudRepository<ChatReaction,Integer> {
        @Modifying
        @Query(value = "DELETE FROM chat_reaction WHERE chat_room_id=?1", nativeQuery = true)
        void deleteChatReactionsByChatRoomId(Integer chatRoomId);

        @Modifying
        @Query(value="DELETE FROM chat_reaction WHERE chat_record_id=?1", nativeQuery = true)
        void deleteChatReactionsByChatRecordId(Integer chatRecordId);

        @Modifying
        @Query(value="UPDATE chat_reaction SET reactor_name=?1, reactor_username=?2 WHERE reactor_id=?3", nativeQuery = true)
        void updateChatReactionReactorNameByReactorId(String reactorName, String reactorUsername, Integer reactorId);



}
