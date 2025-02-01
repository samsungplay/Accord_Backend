package com.infiniteplay.accord.services;

import com.infiniteplay.accord.utils.ArrayUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final ThreadPoolTaskScheduler accordTaskScheduler;

    private final Map<Integer, Function<Object[],Void>> functionMap = new HashMap<>();
    private final Map<Integer, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    private final Map<Integer, Object[]> argsMap = new HashMap<>();

    public void scheduleTask(Runnable task, Instant instant) {
        accordTaskScheduler.schedule(task, instant);
    }

    public void scheduleFlexibleTask(Function<Object[],Void> function, Instant instant, int key, Object[] args) {
        ScheduledFuture<?> future = accordTaskScheduler.schedule(() -> {
            function.apply(args);
            functionMap.remove(key);
            scheduledFutureMap.remove(key);
            argsMap.remove(key);
        }, instant);
        functionMap.put(key, function);
        scheduledFutureMap.put(key,future);
        argsMap.put(key,args);
    }



    public void runImmediately(int key, long delay, Object[] extraArgs) {
        ScheduledFuture<?> future = scheduledFutureMap.remove(key);
        if(future != null) {
            future.cancel(false);
            Function<Object[], Void> function = functionMap.remove(key);
            Object[] args = argsMap.remove(key);
            if(function != null && args != null) {
                accordTaskScheduler.schedule(() -> {
                    function.apply(ArrayUtils.concatArrays(args, extraArgs));
                }, Instant.now().plusMillis(delay));
            }
        }


    }

    public void cancelImmediately(int key) {
        ScheduledFuture<?> future = scheduledFutureMap.remove(key);
        if(future != null) {
            future.cancel(true);

        }


    }


}
