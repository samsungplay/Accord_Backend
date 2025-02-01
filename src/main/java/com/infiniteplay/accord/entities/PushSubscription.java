package com.infiniteplay.accord.entities;

import jakarta.persistence.*;

@Entity
public class PushSubscription extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(unique = true)
    private Integer userId;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String loginSessionToken;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getId() {
        return id;
    }

    public String getLoginSessionToken() {
        return loginSessionToken;
    }

    public void setLoginSessionToken(String loginSessionToken) {
        this.loginSessionToken = loginSessionToken;
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

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String publicKey) {
        this.p256dh = publicKey;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }
}
