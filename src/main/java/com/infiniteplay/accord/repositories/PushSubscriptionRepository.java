package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Integer> {

    PushSubscription findByUserId(Integer userId);

    List<PushSubscription> findByUserIdIn(List<Integer> userIds);
}
