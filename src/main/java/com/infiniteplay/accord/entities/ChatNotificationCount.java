package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

@Entity
@Table(name = "chat_notification_count", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "chatroom_id"})
},
        indexes = {
                @Index(columnList = "chatroom_id")
        }
)
public class ChatNotificationCount extends BaseEntity {

    @Id
    @GeneratedValue
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "chatroom_id", nullable = false)
    private Integer chatRoomId;

    @Column(nullable = false)
    private Integer count = 0;

    @Column(name = "latest_message_id", nullable = true)
    private Integer latestMessageId;

    @Column(name = "first_unread_timestamp", nullable = false)
    private Long firstUnreadTimestamp;


    public Long getFirstUnreadTimestamp() {
        return firstUnreadTimestamp;
    }

    public void setFirstUnreadTimestamp(Long firstUnreadTimestamp) {
        this.firstUnreadTimestamp = firstUnreadTimestamp;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(Integer chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getLatestMessageId() {
        return latestMessageId;
    }

    public void setLatestMessageId(Integer latestMessageId) {
        this.latestMessageId = latestMessageId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ChatNotificationCount other = (ChatNotificationCount) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
