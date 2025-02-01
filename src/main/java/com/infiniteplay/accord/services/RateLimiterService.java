package com.infiniteplay.accord.services;


import com.infiniteplay.accord.entities.RateLimitBucket;
import com.infiniteplay.accord.repositories.RateLimitBucketRepository;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.InternalException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.LocalBucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private final RateLimitBucketRepository rateLimitBucketRepository;

    @Transactional
    public void freeCache(String url, String usernameWithId) {
        String bucketId = url + "@" + usernameWithId;
        RateLimitBucket rateLimitBucket = rateLimitBucketRepository.findByBucketId(bucketId);
        if (rateLimitBucket != null) {
            rateLimitBucketRepository.delete(rateLimitBucket);
        }
    }

    @Transactional
    public boolean limitRate(String url, String usernameWithId, int capacity, Duration duration) throws InternalException {
        String bucketId = url + "@" + usernameWithId;
        RateLimitBucket rateLimitBucket = rateLimitBucketRepository.findByBucketId(bucketId);
        LocalBucket bucket = null;
        boolean ok = false;
        if(rateLimitBucket == null) {

            bucket = Bucket.builder().addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity,duration).build()).build();

            try {
                ok = bucket.tryConsume(1);
                rateLimitBucketRepository.save(new RateLimitBucket(null,bucketId,bucket.toBinarySnapshot()));
            } catch (IOException e) {
                throw new InternalException(e.getMessage());
            }
        }
        else {
            try {
                bucket = LocalBucket.fromBinarySnapshot(rateLimitBucket.getBucketData());
                ok = bucket.tryConsume(1);
                rateLimitBucket.setBucketData(bucket.toBinarySnapshot());
                rateLimitBucketRepository.save(rateLimitBucket);
            } catch (IOException e) {
                throw new InternalException(e.getMessage());
            }
        }


        return ok;
    }
}
