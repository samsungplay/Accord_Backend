package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.ChatRecord;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.ChatRecordSearchFilter;
import com.infiniteplay.accord.models.ContentFilterFlag;
import com.infiniteplay.accord.models.SearchOrder;
import com.infiniteplay.accord.utils.ChatException;
import com.infiniteplay.accord.utils.GenericException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.*;

@SuppressWarnings("UnusedAssignment")
@Repository
public class CustomChatRecordRepositoryImpl implements CustomChatRecordRepository {
    @PersistenceContext
    private EntityManager em;
    @Value("${chatrecord.perpagecount}")
    private int perPageCount;

    public CustomChatRecordRepositoryImpl() {
    }

    @Override
    public List<ChatRecord> searchChatRecord(@Nullable ChatRoom chatRoom, Set<User> blockeds, List<ChatRecordSearchFilter> filters, Integer cursorId, SearchOrder order, boolean previous, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag
            , boolean querySpam, @Nullable Set<ChatRoom> userChatRooms, @Nullable Set<User> customUserFilter) throws GenericException {
        //default query to fetch a page of chat record without any filter applied.
        List<ChatRecord> records = new ArrayList<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder queryBuilder2 = new StringBuilder();
        String defaultQuery = "SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where";
        String defaultQuery2 = "SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where";
        queryBuilder.append(defaultQuery);
        queryBuilder2.append(defaultQuery2);


        String orderString = "";

        if (cursorId > 0) {
            if (previous && order == SearchOrder.NEW) {
                queryBuilder.append(" (c.id > " + cursorId + ")");
                queryBuilder2.append(" (c.id > " + cursorId + ")");
                orderString = "order by c.id ASC";
            } else if (previous && order == SearchOrder.OLD) {
                queryBuilder.append(" (c.id < " + cursorId + ")");
                queryBuilder2.append(" (c.id < " + cursorId + ")");
                orderString = "order by c.id DESC";
            } else if (!previous && order == SearchOrder.NEW) {
                queryBuilder.append(" (c.id < " + cursorId + ")");
                queryBuilder2.append(" (c.id < " + cursorId + ")");
                orderString = "order by c.id DESC";
            } else if (!previous && order == SearchOrder.OLD) {
                queryBuilder.append(" (c.id > " + cursorId + ")");
                queryBuilder2.append(" (c.id > " + cursorId + ")");
                orderString = "order by c.id ASC";
            }
        } else {
            if (order == SearchOrder.NEW) {
                orderString = "order by c.id DESC";
            } else {
                orderString = "order by c.id ASC";
            }
        }

        if (!querySpam) {
            if (cursorId > 0) {
                queryBuilder.append(" AND (c.chatRoom= :chatRoom) AND (c.type <> 'text' or c.sender not in :blockeds) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and c.scheduledTime IS NULL");
                queryBuilder2.append(" AND (c.chatRoom= :chatRoom) AND (c.type <> 'text' or c.sender not in :blockeds) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and c.scheduledTime IS NULL");
            } else {
                queryBuilder.append(" (c.chatRoom= :chatRoom) AND (c.type <> 'text' or c.sender not in :blockeds) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and c.scheduledTime IS NULL");
                queryBuilder2.append(" (c.chatRoom= :chatRoom) AND (c.type <> 'text' or c.sender not in :blockeds) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and c.scheduledTime IS NULL");
            }
        } else {
            if (cursorId > 0) {
                queryBuilder.append(" AND (c.chatRoom in :chatRooms) AND (c.type = 'text' and c.sender not in :blockeds) AND c.scheduledTime IS NULL");
                queryBuilder2.append(" AND (c.chatRoom in :chatRooms) AND (c.type = 'text' and c.sender not in :blockeds) AND c.scheduledTime IS NULL");
            } else {
                queryBuilder.append(" (c.chatRoom in :chatRooms) AND (c.type = 'text' and c.sender not in :blockeds) AND c.scheduledTime IS NULL");
                queryBuilder2.append(" (c.chatRoom in :chatRooms) AND (c.type = 'text' and c.sender not in :blockeds) AND c.scheduledTime IS NULL");
            }
        }


        for (ChatRecordSearchFilter filter : filters) {
            queryBuilder.append(" AND " + filter.getQuery());
            queryBuilder2.append(" AND " + filter.getQuery());
        }

        if (querySpam) {
            spamFlag = ContentFilterFlag.INCLUDE;
        }

        queryBuilder.append(getFlags(nsfwFlag, spamFlag));
        queryBuilder2.append(getFlags(nsfwFlag, spamFlag));

        if (customUserFilter != null) {
            queryBuilder.append(" and c.sender in :users");
            queryBuilder2.append(" and c.sender in :users");
        }


        queryBuilder.append(" " + orderString);
        queryBuilder2.append(" " + orderString);

        System.out.println("QUERYMATCHER::" + queryBuilder.toString());
        System.out.println("QUERYMATCHER::" + queryBuilder2.toString());

        TypedQuery<ChatRecord> searchQuery = em.createQuery(queryBuilder.toString(), ChatRecord.class);
        TypedQuery<ChatRecord> searchQuery2 = em.createQuery(queryBuilder2.toString(), ChatRecord.class);


        searchQuery.setParameter("blockeds", blockeds);
        searchQuery2.setParameter("blockeds", blockeds);
        if (!querySpam) {
            searchQuery.setParameter("chatRoom", chatRoom);
            searchQuery.setParameter("forUserId", "system_private_" + forUserId + "_");
            searchQuery2.setParameter("chatRoom", chatRoom);
            searchQuery2.setParameter("forUserId", "system_private_" + forUserId + "_");
        } else {
            searchQuery.setParameter("chatRooms", userChatRooms);
            searchQuery2.setParameter("chatRooms", userChatRooms);
        }

        if (customUserFilter != null) {
            searchQuery.setParameter("users", customUserFilter);
            searchQuery2.setParameter("users", customUserFilter);
        }

        for (ChatRecordSearchFilter filter : filters) {
            if (filter.getQueryParameters() != null) {
                filter.getQueryParameters().forEach((key, value) -> {
                    searchQuery.setParameter((String) key, value);
                    searchQuery2.setParameter((String) key, value);
                });
            }
        }

        records = searchQuery.setMaxResults(30).getResultList();
        records = searchQuery2.setMaxResults(30).getResultList();

        if (previous) {
            Collections.reverse(records);
        }


        return records;
    }

    @Override
    public List<ChatRecord> getNextPageSpam(Set<ChatRoom> userChatRooms, Integer prevPageLastId, Set<User> blockeds, ContentFilterFlag nsfwFlag,
                                            @Nullable Set<User> customUserFilter) {
        if (prevPageLastId.equals(0)) {
            //simply fetch the first 20 entries
            List<ChatRecord> records = null;


            TypedQuery<ChatRecord> recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                            " (c.chatRoom in :chatRooms) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                            (customUserFilter != null ? " and c.sender in :users" : "") +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRooms", userChatRooms)
                    .setParameter("blockeds", blockeds)
                    .setMaxResults(perPageCount);
            if (customUserFilter != null) {
                recordsQuery.setParameter("users", customUserFilter);
            }

            records = recordsQuery.getResultList();


            recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                            " (c.chatRoom in :chatRoom) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                            (customUserFilter != null ? " and c.sender in :users" : "") +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRoom", userChatRooms)
                    .setParameter("blockeds", blockeds)
                    .setMaxResults(perPageCount);
            if (customUserFilter != null) {
                recordsQuery.setParameter("users", customUserFilter);
            }
            records = recordsQuery.getResultList();

            return records;
        } else {
            List<ChatRecord> records = null;
            TypedQuery<ChatRecord> recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                            " c.id < :lastId and (c.chatRoom in :chatRooms) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                            (customUserFilter != null ? " and c.sender in :users" : "") +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRooms", userChatRooms)
                    .setParameter("blockeds", blockeds)
                    .setParameter("lastId", prevPageLastId)
                    .setMaxResults(perPageCount);

            if (customUserFilter != null) {
                recordsQuery.setParameter("users", customUserFilter);
            }

            records = recordsQuery.getResultList();

            recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                            " c.id < :lastId and (c.chatRoom in :chatRooms) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                            (customUserFilter != null ? " and c.sender in :users" : "") +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRooms", userChatRooms)
                    .setParameter("blockeds", blockeds)
                    .setParameter("lastId", prevPageLastId)
                    .setMaxResults(perPageCount);

            if (customUserFilter != null) {
                recordsQuery.setParameter("users", customUserFilter);
            }
            records = recordsQuery.getResultList();
            return records;
        }
    }

    @Override
    public List<ChatRecord> getPrevPageSpam(Set<ChatRoom> userChatRooms, Integer nextPageFirstId, Set<User> blockeds, ContentFilterFlag nsfwFlag,
                                            @Nullable Set<User> customUserFilter) {

        List<ChatRecord> records = null;
        TypedQuery<ChatRecord> recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                        " c.id > :firstId and (c.chatRoom in :chatRooms) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                        (customUserFilter != null ? " and c.sender in :users" : "") +
                        " order by c.id asc", ChatRecord.class)
                .setParameter("chatRooms", userChatRooms)
                .setParameter("blockeds", blockeds)
                .setParameter("firstId", nextPageFirstId)
                .setParameter("users", customUserFilter)
                .setMaxResults(perPageCount);

        if (customUserFilter != null) {
            recordsQuery.setParameter("users", customUserFilter);
        }
        records = recordsQuery.getResultList();
        recordsQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                        " c.id > :firstId and (c.chatRoom in :chatRooms) and (c.type = 'text') and (c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE) +
                        (customUserFilter != null ? " and c.sender in :users" : "") +
                        " order by c.id asc", ChatRecord.class)
                .setParameter("chatRooms", userChatRooms)
                .setParameter("blockeds", blockeds)
                .setParameter("firstId", nextPageFirstId)
                .setParameter("users", customUserFilter)
                .setMaxResults(perPageCount);

        if (customUserFilter != null) {
            recordsQuery.setParameter("users", customUserFilter);
        }
        records = recordsQuery.getResultList();
        Collections.reverse(records);

        return records;
    }

    private String getFlags(ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {
        String flags = "";
        if (nsfwFlag == ContentFilterFlag.INCLUDE && spamFlag == ContentFilterFlag.INCLUDE) {
            flags = " AND c.isSpam = TRUE and c.isNsfw = TRUE";
        } else if (nsfwFlag == ContentFilterFlag.EXCLUDE && spamFlag == ContentFilterFlag.INCLUDE) {
            flags = " AND c.isSpam = TRUE and c.isNsfw = FALSE";
        } else if (nsfwFlag == ContentFilterFlag.INCLUDE && spamFlag == ContentFilterFlag.EXCLUDE) {
            flags = " AND c.isSpam = FALSE and c.isNsfw = TRUE";
        } else if (nsfwFlag == ContentFilterFlag.EXCLUDE && spamFlag == ContentFilterFlag.EXCLUDE) {
            flags = " AND c.isSpam = FALSE and c.isNsfw = FALSE";
        } else if (nsfwFlag == ContentFilterFlag.EXCLUDE && spamFlag == ContentFilterFlag.ANY) {
            flags = " AND c.isNsfw = FALSE";
        } else if (nsfwFlag == ContentFilterFlag.ANY && spamFlag == ContentFilterFlag.EXCLUDE) {
            flags = " AND c.isSpam = FALSE";
        } else if (nsfwFlag == ContentFilterFlag.INCLUDE && spamFlag == ContentFilterFlag.ANY) {
            flags = " AND c.isNsfw = TRUE";
        } else if (nsfwFlag == ContentFilterFlag.ANY && spamFlag == ContentFilterFlag.INCLUDE) {
            flags = " AND c.isSpam = TRUE";
        }
        return flags;
    }


    @Override
    public boolean hasChatRecordById(ChatRoom chatRoom, Set<User> blockeds, Integer id, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {


        Long count = (Long) (em.createQuery("SELECT COUNT(c) from ChatRecord c WHERE c.id = :id and c.chatRoom = :chatRoom and " +
                "(c.type <> 'text' or c.sender not in :blockeds) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag)))
                .setParameter("id", id)
                .setParameter("chatRoom", chatRoom)
                .setParameter("blockeds", blockeds)
                .setParameter("forUserId", "system_private_" + forUserId + "_")
                .getSingleResult();

        return count > 0;
    }

    @Override
    public boolean hasSpamChatRecordById(Set<ChatRoom> userChatRooms, Set<User> blockeds, Integer id, ContentFilterFlag nsfwFlag,
                                         @Nullable Set<User> customUserFilter) {


        Query countQuery = em.createQuery("SELECT COUNT(c) from ChatRecord c WHERE c.id = :id and c.chatRoom in :chatRooms and " +
                        "(c.type = 'text' and c.sender not in :blockeds) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, ContentFilterFlag.INCLUDE)
                        + (customUserFilter != null ? " and c.sender in :users" : "")
                )
                .setParameter("id", id)
                .setParameter("chatRooms", userChatRooms)
                .setParameter("blockeds", blockeds);

        if (customUserFilter != null) {
            countQuery.setParameter("users", customUserFilter);
        }

        return (Long) countQuery.getSingleResult() > 0;
    }

    @Override
    public List<ChatRecord> getNextPageByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, Integer prevPageLastId, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {
        if (prevPageLastId.equals(0)) {
            //simply fetch the first 20 entries
            List<ChatRecord> records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                            " (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRoom", chatRoom)
                    .setParameter("blockeds", blockeds)
                    .setParameter("forUserId", "system_private_" + forUserId + "_")
                    .setMaxResults(perPageCount)
                    .getResultList();

            records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                            " (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRoom", chatRoom)
                    .setParameter("blockeds", blockeds)
                    .setParameter("forUserId", "system_private_" + forUserId + "_")
                    .setMaxResults(perPageCount)
                    .getResultList();
            return records;
        } else {
            List<ChatRecord> records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                            " c.id < :lastId and (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRoom", chatRoom)
                    .setParameter("blockeds", blockeds)
                    .setParameter("lastId", prevPageLastId)
                    .setParameter("forUserId", "system_private_" + forUserId + "_")
                    .setMaxResults(perPageCount)
                    .getResultList();

            records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                            " c.id < :lastId and (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                            " order by c.id desc", ChatRecord.class)
                    .setParameter("chatRoom", chatRoom)
                    .setParameter("blockeds", blockeds)
                    .setParameter("lastId", prevPageLastId)
                    .setParameter("forUserId", "system_private_" + forUserId + "_")
                    .setMaxResults(perPageCount)
                    .getResultList();

            return records;
        }
    }

    @Override
    public List<ChatRecord> getPrevPageByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, Integer nextPageFirstId, int forUserId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {
        List<ChatRecord> records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                        " c.id > :firstId and (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.id asc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .setParameter("blockeds", blockeds)
                .setParameter("firstId", nextPageFirstId)
                .setParameter("forUserId", "system_private_" + forUserId + "_")
                .setMaxResults(perPageCount)
                .getResultList();

        records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                        " c.id > :firstId and (c.chatRoom= :chatRoom and (c.type <> 'text' or c.sender not in :blockeds)) and (c.type NOT LIKE 'system_private%' or c.type LIKE CONCAT(:forUserId,'%')) and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.id asc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .setParameter("blockeds", blockeds)
                .setParameter("firstId", nextPageFirstId)
                .setParameter("forUserId", "system_private_" + forUserId + "_")
                .setMaxResults(perPageCount)
                .getResultList();

        Collections.reverse(records);

        return records;
    }

    @Override
    public List<ChatRecord> getAllPinnedByChatRoomIdBlockFiltered(ChatRoom chatRoom, Set<User> blockeds, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {
        List<ChatRecord> records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                        " (c.chatRoom= :chatRoom and c.sender not in :blockeds) and c.pinned = true and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.date desc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .setParameter("blockeds", blockeds)
                .getResultList();

        records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                        " (c.chatRoom= :chatRoom and c.sender not in :blockeds) and c.pinned = true and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.date desc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .setParameter("blockeds", blockeds)
                .getResultList();

        return records;
    }

    @Override
    public List<ChatRecord> getAllPinnedByChatRoomId(ChatRoom chatRoom, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {

        List<ChatRecord> records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                        " c.chatRoom= :chatRoom and c.pinned = true and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.date desc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .getResultList();

        records = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                        " c.chatRoom= :chatRoom and c.pinned = true and (c.scheduledTime IS NULL)" + getFlags(nsfwFlag, spamFlag) +
                        " order by c.date desc", ChatRecord.class)
                .setParameter("chatRoom", chatRoom)
                .getResultList();

        return records;
    }

    @Override
    public List<ChatRecord> getScheduledRecords(@Nullable ChatRoom chatRoom, @Nullable User scheduler, @Nullable Long currentTime) {
        StringBuilder extraQueryBuilder = new StringBuilder();

        if (chatRoom != null) {
            extraQueryBuilder.append(" and c.chatRoom= :chatRoom");
        }
        if (scheduler != null) {
            extraQueryBuilder.append(" and c.sender= :sender");
        }
        if (currentTime != null) {
            extraQueryBuilder.append(" and c.scheduledTime <= :currentTime");
        } else {
            extraQueryBuilder.append(" and c.scheduledTime is not null");
        }
        String extraQuery = extraQueryBuilder.toString();
        extraQuery = extraQuery.substring(extraQuery.indexOf(" and") + " and".length());
        TypedQuery<ChatRecord> recordQuery = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.chatReactions cr where" +
                extraQuery +
                " order by c.date desc", ChatRecord.class);

        TypedQuery<ChatRecord> recordQuery2 = em.createQuery("SELECT c from ChatRecord c LEFT JOIN FETCH c.sender LEFT JOIN FETCH c.poll LEFT JOIN FETCH c.replyTargetSender LEFT JOIN FETCH c.pollVotes p LEFT JOIN FETCH p.voter where" +
                extraQuery +
                " order by c.date desc", ChatRecord.class);
        if (chatRoom != null) {
            recordQuery.setParameter("chatRoom", chatRoom);
            recordQuery2.setParameter("chatRoom", chatRoom);
        }
        if (scheduler != null) {
            recordQuery.setParameter("sender", scheduler);
            recordQuery2.setParameter("sender", scheduler);
        }
        if (currentTime != null) {
            recordQuery.setParameter("currentTime", currentTime);
            recordQuery2.setParameter("currentTime", currentTime);
        }

        List<ChatRecord> records = recordQuery.getResultList();
        records = recordQuery2.getResultList();

        return records;
    }
}
