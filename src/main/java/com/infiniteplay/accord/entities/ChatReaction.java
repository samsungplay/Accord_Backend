package com.infiniteplay.accord.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

@Entity
@Table(name="chat_reaction",indexes={
        @Index(columnList = "chat_room_id"),
        @Index(columnList = "chat_record_id"),
        @Index(columnList = "reactor_id")
})
public class ChatReaction extends BaseEntity {

    public ChatReaction() {

    }
    public ChatReaction(Integer id, String code, Integer reactorId, Integer recordId, Integer chatRoomId, ChatRecord chatRecord, String reactorName, String reactorUsername) {
        this.id = id;
        this.code = code;
        this.reactorId = reactorId;
        this.recordId = recordId;
        this.chatRoomId = chatRoomId;
        this.chatRecord = chatRecord;
        this.reactorName = reactorName;
        this.reactorUsername = reactorUsername;
    }

    public ChatReaction(Integer id, String code, Integer reactorId) {
        this.id = id;
        this.code = code;
        this.reactorId = reactorId;
    }

    @GeneratedValue(strategy = GenerationType.AUTO)
    @Id
    private Integer id;

    @Column(length = 100)
    private String code;

    private Integer reactorId;

    private Integer recordId;



    @JsonIgnore
    private Integer chatRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="chat_record_id")
    @JsonIgnore
    private ChatRecord chatRecord;

    private String reactorName;

    @Column(name="reactor_username")
    private String reactorUsername;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ChatReaction other = (ChatReaction) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getReactorId() {
        return reactorId;
    }

    public void setReactorId(Integer reactorId) {
        this.reactorId = reactorId;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    public Integer getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(Integer chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public ChatRecord getChatRecord() {
        return chatRecord;
    }

    public void setChatRecord(ChatRecord chatRecord) {
        this.chatRecord = chatRecord;
    }

    public String getReactorName() {
        return reactorName;
    }

    public void setReactorName(String reactorName) {
        this.reactorName = reactorName;
    }

    public String getReactorUsername() {
        return reactorUsername;
    }

    public void setReactorUsername(String reactorUsername) {
        this.reactorUsername = reactorUsername;
    }
}
