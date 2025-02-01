package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.RateLimitBucket;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitBucketRepository extends CrudRepository<RateLimitBucket, Integer> {
    RateLimitBucket findByBucketId(String bucketId);
}
