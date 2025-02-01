package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRoomRoleSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRoleSettingsRepository extends JpaRepository<ChatRoomRoleSettings, Integer> {
    ChatRoomRoleSettings findByChatRoomId(Integer chatRoomId);

    void deleteByChatRoomId(Integer chatRoomId);
}
