package com.vj.rateLimiter.controller;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/slidingWindowLog")
public class SlidingWindowLogController {
  
  private Map<String, Set<Long>> windowLog = new ConcurrentHashMap<>();
  private final int WINDOW_SIZE = 10 * 1000;
  private final int MAX_REQUESTS = 1;
  
  @GetMapping("/check")
  public ResponseEntity<String> check(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    long now = new Date().getTime();
    long windowStart = now - WINDOW_SIZE;
    
    windowLog.putIfAbsent(remoteAddr, new TreeSet<>());

    // synchronize on the set of epochs.
    synchronized (windowLog.get(remoteAddr)) {
      Set<Long> epochs = windowLog.get(remoteAddr);
      // prune epochs older than WINDOW_SIZE
      for (Long time : epochs) {
        if (now - time > WINDOW_SIZE) {
          System.out.println("Removing " + time);
          epochs.remove(time);
        } else {
          break;
        }
      }
      
      // allow if still there is a space left, else reject request.
      if (epochs.size() < MAX_REQUESTS) {
        epochs.add(now);
        return new ResponseEntity<>("Ok", HttpStatus.OK);
      } else {
        return new ResponseEntity<>("Too Many requests", HttpStatus.TOO_MANY_REQUESTS);
      }
    }
    
  }

}
