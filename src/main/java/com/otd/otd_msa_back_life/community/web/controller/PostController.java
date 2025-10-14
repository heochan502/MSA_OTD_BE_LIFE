package com.otd.otd_msa_back_life.community.web.controller;

import com.otd.otd_msa_back_life.community.repository.CommunityPostRepository;
import com.otd.otd_msa_back_life.community.repository.MentRepository;
import com.otd.otd_msa_back_life.community.service.PostService;
import com.otd.otd_msa_back_life.community.web.dto.post.*;
import com.otd.otd_msa_back_life.configuration.model.ResultResponse;
import com.otd.otd_msa_back_life.configuration.model.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/OTD/community")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommunityPostRepository communityPostRepository;
    private final MentRepository mentRepository;

    // 생성
    @PostMapping("/posts")
    public ResponseEntity<PostRes> create(
            @Valid @RequestBody PostCreateReq req,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            UriComponentsBuilder uriBuilder
    ) {
        Long requesterId = userPrincipal.getSignedUserId();
        PostRes res = postService.create(req, requesterId);
        return ResponseEntity
                .created(uriBuilder.path("/api/OTD/community/posts/{id}")
                        .buildAndExpand(res.getPostId()).toUri())
                .body(res);
    }

    // 목록
    @GetMapping("/posts")
    public Page<PostListRes> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryKey,
            @RequestParam(required = false) String searchText
    ) {
        return postService.list(page, size, categoryKey, searchText);
    }

    // 상세
    @GetMapping("/posts/{postId}")
    public PostRes get(@PathVariable Long postId) {
        return postService.get(postId);
    }

    // ⚠️ 중복이던 파일목록 엔드포인트(/posts/{postId}/files)는 제거했습니다.
    // 파일 목록/업로드/삭제는 PostFileController 가 전담합니다.

    // 수정
    @PutMapping("/posts/{postId}")
    public PostRes update(
            @PathVariable Long postId,
            @RequestBody PostUpdateReq req,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long requesterId = userPrincipal.getSignedUserId();
        return postService.update(postId, req, requesterId);
    }

    // 삭제
    @DeleteMapping("/posts/{postId}")
    public void delete(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = "X-ROLE", required = false) String role) {
        Long requesterId = userPrincipal.getSignedUserId();
        postService.deleteSoft(postId, requesterId, role);
    }

    @PutMapping("/posts/modify/nickname")
    public ResultResponse<?> updateNickName(@AuthenticationPrincipal UserPrincipal userPrincipal
            , @RequestBody ProfilePicPutDto dto) {
        Long userId = userPrincipal.getSignedUserId();
        dto.setUserId(userId);
        int result = postService.updateNickName(dto);
        return new ResultResponse<>("life 서버 닉네임 수정 완료", result);
    }

    @PutMapping("/posts/modify/profile")
    public ResultResponse<?> modifyProfile(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                           @RequestBody ProfilePicPutDto dto) {
        Long userId = userPrincipal.getSignedUserId();
        dto.setUserId(userId);
        int result = postService.updateImgPath(dto);
        return new ResultResponse<>("life 서버 이미지 수정 완료", result);
    }

    @PutMapping("/posts/modify/info")
    public ResultResponse<?> updateUserInfo(@RequestBody ProfilePicPutDto dto) {
        int result = postService.updateUserInfo(dto);
        return new ResultResponse<>("life 서버 유저정보 수정 완료", result);
    }
}
