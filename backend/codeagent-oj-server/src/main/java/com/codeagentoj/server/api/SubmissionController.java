package com.codeagentoj.server.api;

import com.codeagentoj.server.submission.SubmissionDtos.*;
import com.codeagentoj.server.submission.SubmissionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final SubmissionService service; public SubmissionController(SubmissionService service){this.service=service;}
    @PostMapping public ApiResponse<Summary> create(@Valid @RequestBody CreateRequest req,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.create(uid(jwt),req));}
    @GetMapping public ApiResponse<Page> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                             @RequestParam(required=false) String status,@RequestParam(required=false) String slug,
                                             @AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.list(uid(jwt),page,size,status,slug));}
    @GetMapping("/{id}") public ApiResponse<Detail> detail(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.detail(id,uid(jwt)));}
    @PostMapping("/{id}/rejudge") public ApiResponse<Summary> rejudge(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.rejudge(id,uid(jwt)));}
    /** 我解出的题目 slug（题库页据此标记"已通过"）。字面量路径优先于 /{id}，不会冲突。 */
    @GetMapping("/solved") public ApiResponse<java.util.List<String>> solved(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.solvedProblemSlugs(uid(jwt)));}
    @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter events(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){long user=uid(jwt);SseEmitter emitter=new SseEmitter(Duration.ofMinutes(5).toMillis()); Executors.newSingleThreadExecutor().execute(()->{try{String last="";for(int i=0;i<600;i++){var event=service.events(id,user).getFirst();if(!event.status().equals(last)){emitter.send(SseEmitter.event().name(event.type()).data(event));last=event.status();}if(List.of("AC","WA","CE","RE","TLE","MLE").contains(event.status())){emitter.complete();return;}Thread.sleep(500);}emitter.complete();}catch(Exception e){emitter.completeWithError(e);}});return emitter;}
    private long uid(Jwt jwt){if(jwt==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"需要登录");return Long.parseLong(jwt.getSubject());}
}
