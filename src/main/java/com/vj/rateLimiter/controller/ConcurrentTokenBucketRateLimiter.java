package com.vj.rateLimiter.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/token")
public class ConcurrentTokenBucketRateLimiter {
  
  // to productise this
  // 1) Make maxtokens configurable.
  // 2) Make refill value configurable.
  // 3) Make refill time interval configurable.
  
  private final Map<String, AtomicInteger> tokenBucket = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  private static final int maxTokens = 10;
  private static final AtomicInteger counter = new AtomicInteger(0);
  private static final AtomicInteger allowedCounter = new AtomicInteger(0);
  private static final AtomicInteger exceededCounter = new AtomicInteger(0);
  private static final AtomicInteger refillCounter = new AtomicInteger(0);
  private static final int incrementBy = 1;

  @GetMapping("/limited")
  public ResponseEntity<String> limited(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    counter.incrementAndGet();
    // add initial tokens for key, if absent.
    tokenBucket.putIfAbsent(remoteAddr, new AtomicInteger(10));
    
    // conditional atomic update 
    int prev = tokenBucket.get(remoteAddr).getAndUpdate(i -> {
      if (i > 0)
        return i - 1;
      return 0;
    });
    
    // no tokens, send 429
    if (prev == 0) {
      exceededCounter.incrementAndGet();
      return new ResponseEntity<>("Rate limit reached!", HttpStatus.TOO_MANY_REQUESTS);
    } else {
      allowedCounter.incrementAndGet();
      return new ResponseEntity<>("Limited, don't over use me!", HttpStatus.OK);
    }
  }
  
  @PostConstruct
  public void postInitAction() {
    // increment token of each key every 5 secs. 
    scheduledExecutorService.scheduleAtFixedRate(() -> {
      for (String key : tokenBucket.keySet()) {
        int oldValue = tokenBucket.get(key).getAndUpdate(i -> {
          if (i < maxTokens) {
            return i + incrementBy;
          } else {
            return maxTokens;
          }
        });
        if (oldValue != maxTokens) {
          System.out.println((new Date()) + " incremented from " + oldValue);
          refillCounter.incrementAndGet();
        }
      }
    }, 0, 5, TimeUnit.SECONDS);
  }
  
  @GetMapping("/stats")
  public HashMap<String, String> unlimited() {
    HashMap<String, String> stringIntegerHashMap = new HashMap<>();
    stringIntegerHashMap.put("total Requests", counter.toString());
    stringIntegerHashMap.put("served Requests", allowedCounter.toString());
    stringIntegerHashMap.put("rejected Requests", exceededCounter.toString());
    stringIntegerHashMap.put("refill count", refillCounter.toString());
    return stringIntegerHashMap;
  }

  @GetMapping("clearCounters")
  public String clear() {
    tokenBucket.clear();
    counter.set(0);
    allowedCounter.set(0);
    exceededCounter.set(0);
    refillCounter.set(0);
    return "Done";
  }
}
