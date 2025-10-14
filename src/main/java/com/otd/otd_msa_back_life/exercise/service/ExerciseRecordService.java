package com.otd.otd_msa_back_life.exercise.service;

import com.otd.otd_msa_back_life.admin.mapper.AdminMapper;
import com.otd.otd_msa_back_life.admin.model.AdminExerciseDto;
import com.otd.otd_msa_back_life.common.model.PagingDto;
import com.otd.otd_msa_back_life.common.model.PagingReq;
import com.otd.otd_msa_back_life.configuration.model.ResultResponse;
import com.otd.otd_msa_back_life.exercise.entity.ExerciseCatalog;
import com.otd.otd_msa_back_life.exercise.entity.ExerciseRecord;
import com.otd.otd_msa_back_life.exercise.mapper.ExerciseRecordMapper;
import com.otd.otd_msa_back_life.exercise.model.*;
import com.otd.otd_msa_back_life.exercise.repository.ExerciseCatalogRepository;
import com.otd.otd_msa_back_life.exercise.repository.ExerciseRecordRepository;
import com.otd.otd_msa_back_life.feign.ChallengeFeignClient;
import com.otd.otd_msa_back_life.feign.model.ExerciseCountAndSum;
import com.otd.otd_msa_back_life.feign.model.ExerciseDataReq;
import com.otd.otd_msa_back_life.feign.model.ChallengeRecordDeleteReq;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseRecordService {
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExerciseCatalogRepository exerciseCatalogRepository;
    private final ExerciseRecordMapper exerciseRecordMapper;
    private final ChallengeFeignClient challengeFeignClient;
    private final AdminMapper adminMapper;

    public String challengeName(String str) {
        return switch (str) {
            case "조깅" -> "러닝";
            case "하이킹" -> "등산";
            case "요가", "필라테스" -> "자세교정";
            case "실내 사이클", "자전거 타기" -> "라이딩";
            default -> str;
        };
    }
    //    [post] exerciseRecord

    public Long saveExerciseRecord(Long userId, ExerciseRecordPostReq req) {
//        exercise 존재 여부 확인
        ExerciseCatalog exercise = exerciseCatalogRepository.findById(req.getExerciseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 운동입니다."));

//        distance 입력 유효성 검사
        if(exercise.getHasDistance() && req.getDistance() == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "거리기반 운동은 distance 입력이 필수입니다.");
        }
        if (!exercise.getHasDistance() && req.getDistance() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "거리 기반 운동이 아닌 경우 distance 입력이 불가합니다.");
        }
        
//        service 입력 유효성 검사
        if (exercise.getHasReps() && req.getReps() == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반복횟수기반 운동은 reps 입력이 필수입니다. ");
        }
        if (!exercise.getHasReps() && req.getReps() != null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반복횟수기반 운동은 reps 입력이 불가합니다. ");
        }

//                exerciseRecord 생성
        ExerciseRecord exerciseRecord = ExerciseRecord.builder()
                .activityKcal(req.getActivityKcal())
                .effortLevel(req.getEffortLevel())
                .exercise(exercise)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .duration(req.getDuration())
                .distance(req.getDistance())
                .reps(req.getReps())
                .userId(userId)
                .build();
        Long recordId = exerciseRecordRepository.save(exerciseRecord).getExerciseRecordId();

        LocalDate recordDate = req.getStartAt().toLocalDate();
        try {
            ResponseEntity<List<String>> response = challengeFeignClient.getActiveChallengeNames(userId, recordDate);
            List<String> activeChallenges = response.getBody();

            if (activeChallenges != null && !activeChallenges.isEmpty()) {
                ExerciseCountAndSum summary = exerciseRecordRepository.getDailyExerciseSummary(
                        userId,
                        req.getStartAt().toLocalDate().atStartOfDay(),
                        req.getStartAt().toLocalDate().plusDays(1).atStartOfDay()
                );

                for (String ch : activeChallenges) {
                    String mappedChallengeName = challengeName(exercise.getExerciseName());

                    if (ch.equals(mappedChallengeName)) {
                        ExerciseDataReq feign = ExerciseDataReq.builder()
                                .userId(userId)
                                .recordId(recordId)
                                .name(ch)
                                .record(exercise.getHasDistance()
                                        ? req.getDistance()
                                        : req.getReps() != null ? req.getReps().doubleValue() : 0.0)
                                .recordDate(req.getStartAt().toLocalDate())
                                .count(summary.getCount())
                                .totalKcal(summary.getTotalKcal())
                                .today(LocalDate.now())
                                .build();
                        log.info("feign {}", feign);

                        try {
                            ResponseEntity<Integer> response2 = challengeFeignClient.updateProgressByExercise(feign);
                            Integer feignResult = response2.getBody();
                            log.info("result={}", feignResult);
                        } catch (Exception e) {
                            log.error("챌린지 진행도 업데이트 실패 userId={}, recordId={}, error={}",
                                    userId, recordId, e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("활성 챌린지 조회 실패 userId={}, recordDate={}, error={}",
                    userId, recordDate, e.getMessage(), e);
        }

        return recordId;
    }

    //    [GET] recordList -> page

    public List<ExerciseRecordGetRes> getExerciseRecordList(Long userId, PagingReq req) {
        PagingDto dto = PagingDto.builder()
                .type(req.getType())
                .date(req.getDate())
                .size(req.getRowPerPage())
                .startIdx((req.getPage() - 1) * req.getRowPerPage())
                .userId(userId)
                .build();
        return exerciseRecordMapper.findByLimitTo(dto);
    }

    //    [GET] weeklyRecord

    public List<ExerciseRecord> getExerciseRecordWeekly(Long userId, ExerciseRecordWeeklyGetReq req) {
        return exerciseRecordRepository.findByUserIdAndStartAtBetween(userId, req.getStartOfWeek(), req.getEndOfWeek());
    }

//    [GET] detail

    public ExerciseRecordDetailGetRes getExerciseRecordDetail(Long userId, Long exerciseRecordId) {

        ExerciseRecord exerciseRecord = exerciseRecordRepository
                .findByUserIdAndExerciseRecordId(userId, exerciseRecordId);
        ExerciseCatalog exercise = exerciseCatalogRepository.findById(exerciseRecord.getExercise().getExerciseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않은 운동입니다."));

        ExerciseRecordDetailGetRes result = ExerciseRecordDetailGetRes.builder()
                .exerciseRecordId(exerciseRecordId)
                .distance(exerciseRecord.getDistance())
                .reps(exerciseRecord.getReps())
                .activityKcal(exerciseRecord.getActivityKcal())
                .effortLevel(exerciseRecord.getEffortLevel())
                .startAt(exerciseRecord.getStartAt())
                .endAt(exerciseRecord.getEndAt())
                .duration(exerciseRecord.getDuration())
                .exerciseId(exercise.getExerciseId())
                .exerciseName(exercise.getExerciseName())
                .build();

        return result;
    }

//    모든 사용자 평균 운동 소요시간

    public List<AverageExerciseDurationDto> getAverageExerciseDurations() {
        return exerciseRecordMapper.findAverageDurationGroupedByDate();
    }


    //    [DELETE]
    public void deleteExerciseRecord(Long userId, Long exerciseRecordId) {
        ExerciseRecord record = exerciseRecordRepository.findById(exerciseRecordId)
                .orElseThrow(() -> new IllegalArgumentException("운동 기록을 찾을 수 없습니다."));

        if(!record.getUserId().equals(userId)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운동 기록을 삭제할 수 없습니다.");
        }

        String mappedChallengeName = challengeName(record.getExercise().getExerciseName());

        ChallengeRecordDeleteReq req = ChallengeRecordDeleteReq.builder()
                .userId(userId)
                .name(mappedChallengeName)
                .recordId(record.getExerciseRecordId())
                .recordDate(record.getStartAt().toLocalDate())
                .today(LocalDate.now())
                .build();
        exerciseRecordRepository.delete(record);
        ExerciseCountAndSum summary = exerciseRecordRepository.getDailyExerciseSummary(
                userId,
                record.getStartAt().toLocalDate().atStartOfDay(),
                record.getStartAt().toLocalDate().plusDays(1).atStartOfDay()
        );
        req.setCount(summary.getCount());
        req.setTotalKcal(summary.getTotalKcal());
        ResponseEntity<Integer> response = challengeFeignClient.deleteRecordByExercise(req);
        Integer feignResult = response.getBody();
//        exerciseRecordRepository.deleteByUserIdAndExerciseRecordId(userId, exerciseRecordId);
    }

}
