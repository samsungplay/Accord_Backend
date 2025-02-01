package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRecord;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.ChatRecordSearchFilter;
import com.infiniteplay.accord.models.ContentFilterFlag;
import com.infiniteplay.accord.models.SearchOrder;
import com.infiniteplay.accord.utils.GenericException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Set;

public interface CustomChatRecordRepository {
    List<ChatRecord> getNextPageByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, Integer prevPageLastId, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag);

    List<ChatRecord> getPrevPageByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, Integer nextPageFirstId, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag);

    List<ChatRecord> getAllPinnedByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag);

    List<ChatRecord> getAllPinnedByChatRoomId(ChatRoom chatRoom, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag);

    boolean hasChatRecordById(ChatRoom chatRoom, Set<User> blockeds, Integer id, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag);

    boolean hasSpamChatRecordById(Set<ChatRoom> userChatRooms, Set<User> blockeds, Integer id, ContentFilterFlag nsfwFla, @Nullable Set<User> customUserFilter);

    List<ChatRecord> searchChatRecord(@Nullable  ChatRoom chatRoom, Set<User> blockeds, List<ChatRecordSearchFilter> filters, Integer cursorId, SearchOrder order, boolean previous, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag,
                                      boolean forSpam, @Nullable Set<ChatRoom> userChatRooms, @Nullable Set<User> customUserFilter) throws GenericException;


    List<ChatRecord> getNextPageSpam(Set<ChatRoom> userChatRooms, Integer prevPageLastId, Set<User> blockeds, ContentFilterFlag nsfwFlag, Set<User> customUserFilter);

    List<ChatRecord> getPrevPageSpam(Set<ChatRoom> userChatRooms, Integer nextPageFirstId, Set<User> blockeds, ContentFilterFlag nsfwFlag, Set<User> customUserFilter);


}
