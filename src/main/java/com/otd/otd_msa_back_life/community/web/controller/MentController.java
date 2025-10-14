package com.otd.otd_msa_back_life.community.web.controller;

import com.otd.otd_msa_back_life.community.entity.*;
import com.otd.otd_msa_back_life.community.service.MentService;
import com.otd.otd_msa_back_life.community.web.dto.ment.MentCreateReq;
import com.otd.otd_msa_back_life.community.web.dto.ment.MentRes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
// 커뮤니티의 다른 컨트롤러와 동일하게 prefix 정렬
@RequestMapping("/api/OTD/community")
@RequiredArgsConstructor
public class MentController {

    private final MentService mentService;

    // 댓글 목록
    @GetMapping("/posts/{postId}/ments")
    public ResponseEntity<List<MentRes>> list(@PathVariable Long postId) {
        return ResponseEntity.ok(mentService.listByPost(postId));
    }

    // 댓글 생성: 헤더에서 사용자 식별
    @PostMapping("/posts/{postId}/ments")
    public ResponseEntity<MentRes> create(@PathVariable Long postId,
                                          @RequestHeader("X-MEMBER-ID") Long requesterId,
                                          @RequestBody MentCreateReq req) {
        return ResponseEntity.ok(mentService.create(postId, requesterId, req));
    }

    // 댓글 삭제: 본인만 가능
    @DeleteMapping("/ments/{mentId}")
    public ResponseEntity<Void> delete(@PathVariable Long mentId,
                                       @RequestHeader("X-MEMBER-ID") Long requesterId,
                                       @RequestHeader(value = "X-ROLE", required = false) String role) {
        mentService.delete(mentId, requesterId, role);
        return ResponseEntity.noContent().build();
    }
}
