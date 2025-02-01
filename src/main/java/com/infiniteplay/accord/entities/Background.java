package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "background", indexes = {
        @Index(columnList = "chatroom_id"),
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chatroom_id", "name"}),
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class Background extends BaseEntity {


    public Background(Integer id, ChatRoom chatRoom, String name, String file) {
        this.id = id;
        this.chatRoom = chatRoom;
        this.name = name;
        this.file = file;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_id")
    @JsonIgnore
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;


    @Column
    private String name;

    @Column
    private String file;


    public Background() {

    }


    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
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




    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
