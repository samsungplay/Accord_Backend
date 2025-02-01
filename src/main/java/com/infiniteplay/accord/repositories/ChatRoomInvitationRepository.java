package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomInvitationRepository  extends JpaRepository<ChatRoomInvitation, Integer> {
    ChatRoomInvitation findByChatRoomIdAndPermanent(Integer chatRoomId, boolean permanent);
    boolean existsByShortCode(String shortCode);
    void deleteByChatRoomIdAndPermanent(Integer chatRoomId, boolean permanent);
    ChatRoomInvitation findByShortCode(String shortCode);
}
