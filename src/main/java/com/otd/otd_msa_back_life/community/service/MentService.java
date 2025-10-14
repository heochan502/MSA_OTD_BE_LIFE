// MentService.java
package com.otd.otd_msa_back_life.community.service;

import com.otd.otd_msa_back_life.community.client.dto.UserRes;
import com.otd.otd_msa_back_life.community.entity.CommunityPost;
import com.otd.otd_msa_back_life.community.entity.Ment;
import com.otd.otd_msa_back_life.community.repository.CommunityPostRepository;
import com.otd.otd_msa_back_life.community.repository.MentRepository;
import com.otd.otd_msa_back_life.community.web.dto.ment.MentCreateReq;
import com.otd.otd_msa_back_life.community.web.dto.ment.MentRes;
import com.otd.otd_msa_back_life.configuration.user.ProfileUrlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;              // 👈 추가
import org.springframework.beans.factory.annotation.Value; // 👈 추가
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j                                          // 👈 추가
@Service
@RequiredArgsConstructor
public class MentService {

    private final MentRepository mentRepository;
    private final CommunityPostRepository postRepository;
    private final UserServiceProxy userServiceProxy;
    private final ProfileUrlNormalizer profileUrlNormalizer;

    // 🔧 프로필 조회를 환경설정으로 끄고 켤 수 있게
    @Value("${app.user-profile.lookup:true}")
    private boolean userProfileLookupEnabled;

    // MentService.java (핵심 부분만)
    @Transactional
    public MentRes create(Long postId, Long requesterId, MentCreateReq req) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음: " + postId));

        // 1) 우선 유저 서비스 시도
        String profile = null;
        try {
            UserRes u = userServiceProxy.getUserInfo(requesterId);
            profile = profileUrlNormalizer.normalize(u.getProfileImg());
        } catch (Exception ignore) { /* 외부 실패 무시 */ }

        // 2) 실패하면 community_post의 “해당 사용자 최신 글”에서 프로필 폴백
        if (profile == null || profile.isBlank()) {
            CommunityPost last = postRepository.findTopByUserIdOrderByPostIdDesc(requesterId).orElse(null);
            profile = profileUrlNormalizer.normalize(last != null ? last.getProfile() : null);
        }

        Ment saved = mentRepository.save(Ment.builder()
                .post(post)
                .userId(requesterId)
                .content(req.getContent())
                .nickName(req.getNickName())
                .profile(profile)             // ← 스냅샷 저장(있으면)
                .build());

        return MentRes.builder()
                .commentId(saved.getCommentId())
                .postId(post.getPostId())
                .userId(saved.getUserId())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .nickName(saved.getNickName())
                .profile(saved.getProfile() != null ? saved.getProfile()
                        : profileUrlNormalizer.normalize(null)) // 최종 방어
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentRes> listByPost(Long postId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음: " + postId));

        return mentRepository.findByPostPostId(postId).stream()
                .sorted(Comparator.comparing(Ment::getCommentId))
                .map(m -> {
                    // 1) 댓글에 저장된 스냅샷 우선
                    String profile = m.getProfile();

                    // 2) 없으면 유저서비스 → 실패 시 최신 글 프로필 → 그래도 없으면 기본이미지
                    if (profile == null || profile.isBlank()) {
                        try {
                            UserRes u = userServiceProxy.getUserInfo(m.getUserId());
                            profile = profileUrlNormalizer.normalize(u.getProfileImg());
                        } catch (Exception ignore) { /* 외부 실패 무시 */ }

                        if (profile == null || profile.isBlank()) {
                            CommunityPost last = postRepository.findTopByUserIdOrderByPostIdDesc(m.getUserId())
                                    .orElse(null);
                            profile = profileUrlNormalizer.normalize(last != null ? last.getProfile() : null);
                        }
                    }

                    return MentRes.builder()
                            .commentId(m.getCommentId())
                            .postId(post.getPostId())
                            .userId(m.getUserId())
                            .content(m.getContent())
                            .createdAt(m.getCreatedAt())
                            .nickName(m.getNickName())
                            .profile(profile != null ? profile
                                    : profileUrlNormalizer.normalize(null))
                            .build();
                })
                .toList();
    }

    @Transactional
    public void delete(Long mentId, Long requesterId, String role) {
        Ment ment = mentRepository.findById(mentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글 없음: " + mentId));
        boolean isOwner = ment.getUserId().equals(requesterId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (!(isOwner || isAdmin)) {
            throw new IllegalStateException("삭제 권한 없음");
        }

        mentRepository.delete(ment);
    }
}
