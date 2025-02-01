package com.infiniteplay.accord.aspect;

import com.infiniteplay.accord.annotations.EnsureConsistency;
import jakarta.persistence.OptimisticLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Order(1)
public class EnsureConsistencyAspect {

    @Autowired
    TransactionTemplate transactionTemplate;

    @Around("@annotation(com.infiniteplay.accord.annotations.EnsureConsistency)")
    public Object handleOptimisticLockRetry(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EnsureConsistency annotation = signature.getMethod().getAnnotation(EnsureConsistency.class);

        int maxRetries = annotation.maxRetries();
        long delay = annotation.delay();
        int attempts = 0;


        while (attempts < maxRetries) {
            try {
                return transactionTemplate.execute(status -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });
            }
            catch (StaleObjectStateException | OptimisticLockingFailureException | OptimisticLockException e) {
                System.out.println("retry: " + attempts);
                e.printStackTrace();
                attempts++;
                if (attempts >= maxRetries) {
                    throw e;
                }
                Thread.sleep(delay);
            }

        }
        return null;
    }
}
