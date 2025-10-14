package com.otd.otd_msa_back_life.community.service;

import com.otd.otd_msa_back_life.community.entity.CommunityCategory;
import com.otd.otd_msa_back_life.community.entity.CommunityPost;
import com.otd.otd_msa_back_life.community.repository.CommunityCategoryRepository;
import com.otd.otd_msa_back_life.community.repository.CommunityPostRepository;
import com.otd.otd_msa_back_life.community.repository.MentRepository;
import com.otd.otd_msa_back_life.community.web.dto.post.*;
import com.otd.otd_msa_back_life.configuration.user.ProfileUrlNormalizer;
import com.otd.otd_msa_back_life.community.client.dto.UserRes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final CommunityPostRepository postRepository;
    private final CommunityCategoryRepository categoryRepository;
    private final MentRepository mentRepository;
    private final CommunityPostRepository communityPostRepository;

    // ✅ 추가: 프로필 정규화 + 사용자 프로필 조회
    private final ProfileUrlNormalizer profileUrlNormalizer;
    private final UserServiceProxy userServiceProxy;

    /**
     * 게시글 생성
     * - categoryKey로 카테고리 조회 후 엔티티에 세팅
     * - likeCount/isDeleted 기본값 보장
     */
    @Transactional
    public PostRes create(PostCreateReq req, Long requesterId) {
        CommunityCategory category = categoryRepository.findByCategoryKey(req.getCategoryKey())
                .orElseThrow(() -> new IllegalArgumentException("Invalid categoryKey: " + req.getCategoryKey()));

        CommunityPost post = CommunityPost.builder()
                .userId(requesterId)
                .category(category)
                .title(req.getTitle())
                .content(req.getContent())
                .likeCount(0)
                .isDeleted(false)
                .nickName(req.getNickName())
                .profile(req.getProfile()) // 저장은 있는 그대로
                .build();

        CommunityPost saved = postRepository.save(post);
        return toRes(saved); // 응답에서 정규화
    }

    /**
     * 게시글 목록
     * - 프론트가 1-based로 보낸다고 가정하여 0-based로 변환
     * - 기본 정렬: 최신 글(postId desc)
     */
    @Transactional(readOnly = true)
    public Page<PostListRes> list(int page, int size, String categoryKey, String searchText) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postId"));

        boolean hasCat = categoryKey != null && !categoryKey.isBlank();
        boolean hasQ   = searchText != null && !searchText.isBlank();

        Page<CommunityPost> pageData;
        if (hasCat && hasQ) {
            pageData = postRepository.searchInCategory(categoryKey, searchText, pageable);
        } else if (hasCat) {
            pageData = postRepository.findByIsDeletedFalseAndCategory_CategoryKey(categoryKey, pageable);
        } else if (hasQ) {
            pageData = postRepository.search(searchText, pageable);
        } else {
            pageData = postRepository.findByIsDeletedFalse(pageable);
        }

        return pageData.map(p -> PostListRes.builder()
                .postId(p.getPostId())
                .userId(p.getUserId())
                .title(p.getTitle())
                .content(summarize(p.getContent(), 140))
                .likeCount(p.getLikeCount())
                .nickName(p.getNickName())
                .createdAt(p.getCreatedAt())
                .categoryKey(p.getCategory() != null ? p.getCategory().getCategoryKey() : null)
                .commentCount((int) mentRepository.countByPost(p))
                .profile(resolveProfile(p)) // ✅ 목록에도 항상 정규화된 profile 포함
                .build()
        );
    }

    // 본문(content)을 목록에 보여줄 때 너무 길면 잘라내는 유틸
    private String summarize(String text, int max) {
        if (text == null) return "";
        text = text.strip();
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /**
     * 게시글 단건 조회
     */
    @Transactional(readOnly = true)
    public PostRes get(Long postId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음: " + postId));
        return toRes(post);
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public PostRes update(Long postId, PostUpdateReq req, Long requesterId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음: " + postId));

        if (!post.getUserId().equals(requesterId)) {
            throw new IllegalStateException("수정 권한 없음");
        }

        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        return toRes(post);
    }

    /**
     * 게시글 소프트 삭제
     */
    @Transactional
    public void deleteSoft(Long postId, Long requesterId, String role) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음: " + postId));

        boolean isOwner = post.getUserId().equals(requesterId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!(isOwner || isAdmin)) {
            throw new IllegalStateException("삭제 권한 없음");
        }

        post.setIsDeleted(true);
    }

    // ✅ 공통: Post → PostRes 변환 시 항상 프로필 정규화 포함
    private PostRes toRes(CommunityPost p) {
        return PostRes.builder()
                .postId(p.getPostId())
                .userId(p.getUserId())
                .title(p.getTitle())
                .content(p.getContent())
                .likeCount(p.getLikeCount())
                .isDeleted(p.getIsDeleted())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .nickName(p.getNickName())
                .profile(resolveProfile(p)) // ✅ 핵심
                .build();
    }

    /**
     * ✅ 프로필 URL 결정 로직
     * 1) 게시글 row에 profile이 있으면 그걸 우선 사용(정규화)
     * 2) 없으면 UserService에서 profileImg를 가져와 정규화
     *    (실패하면 기본 이미지)
     */
    private String resolveProfile(CommunityPost p) {
        // 1) 게시글 자체에 저장된 값 우선
        String raw = p.getProfile();
        if (raw != null && !raw.isBlank()) {
            return profileUrlNormalizer.normalize(raw);
        }
        // 2) 사용자 서비스에서 조회
        try {
            UserRes user = userServiceProxy.getUserInfo(p.getUserId());
            return profileUrlNormalizer.normalize(user.getProfileImg()); // ← UserRes.profileImg
        } catch (Exception e) {
            return profileUrlNormalizer.normalize(null); // 기본이미지
        }
    }

    @Transactional
    public int updateUserInfo(ProfilePicPutDto dto) {
        int result = communityPostRepository.updateNickNameByUserId(dto.getUserId(), dto.getNickname());
        int result2 = mentRepository.updateNickNameByUserId(dto.getUserId(), dto.getNickname());
        int result3 = communityPostRepository.updateProfileByUserId(dto.getImgPath(), dto.getUserId());
        return result + result2 + result3;
    }

    @Transactional
    public int updateNickName(ProfilePicPutDto dto) {
        int result = communityPostRepository.updateNickNameByUserId(dto.getUserId(), dto.getNickname());
        int result2 = mentRepository.updateNickNameByUserId(dto.getUserId(), dto.getNickname());
        return result + result2;
    }

    @Transactional
    public int updateImgPath(ProfilePicPutDto dto) {
        int result = mentRepository.updateProfileByUserId(dto.getImgPath(), dto.getUserId());
        int result2 = communityPostRepository.updateProfileByUserId(dto.getImgPath(), dto.getUserId());
        return result + result2;
    }
}
