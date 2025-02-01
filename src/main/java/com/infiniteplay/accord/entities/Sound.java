package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "sound", indexes = {
        @Index(columnList = "chatroom_id"),
        @Index(columnList = "order_id"),
},uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chatroom_id","name", "type"})
})
public class Sound extends BaseEntity {


    public Sound(Integer id, ChatRoom chatRoom, String type, String name, String icon, String file, long duration) {
        this.id = id;
        this.chatRoom = chatRoom;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.file = file;
        this.duration = duration;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="chatroom_id")
    @JsonIgnore
    private ChatRoom chatRoom;


    @Column
    private String type;

    @Column
    private String name;

    @Column
    private String icon;

    @Column
    private String file;

    @Column(nullable = false)
    private long duration = 3000;

    @JsonIgnore
    @Column(name="order_id")
    private long orderId = 0;


    public Sound() {

    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

}
