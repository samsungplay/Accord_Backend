package com.infiniteplay.accord.services;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;



public class AuthenticationServiceTest {


    @Test
    public void testComputePasswordStrength() {
           float strength = AuthenticationService.computePasswordStrength("Thisispassword123!!");

           assertThat(strength).isEqualTo(0.7f);
    }
}
