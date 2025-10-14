package com.otd.otd_msa_back_life.body_composition.service;

import com.otd.otd_msa_back_life.body_composition.entity.BasicBodyInfo;
import com.otd.otd_msa_back_life.body_composition.entity.BodyComposition;
import com.otd.otd_msa_back_life.body_composition.entity.BodyCompositionMetric;
import com.otd.otd_msa_back_life.body_composition.mapper.BodyCompositionMapper;
import com.otd.otd_msa_back_life.body_composition.model.*;
import com.otd.otd_msa_back_life.body_composition.repository.BasicBodyInfoRepository;
import com.otd.otd_msa_back_life.body_composition.repository.BodyCompositionMetricRepository;
import com.otd.otd_msa_back_life.body_composition.repository.BodyCompositionRepository;
import com.otd.otd_msa_back_life.common.model.DateRangeDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@ToString
@RequiredArgsConstructor
public class BodyCompositionService {
    private final BodyCompositionRepository bodyCompositionRepository;
    private final BodyCompositionMetricRepository metricRepository;
    private final BodyCompositionMapper bodyCompositionMapper;
    private final BasicBodyInfoRepository basicBodyInfoRepository;

    //    기초 신체 정보 입력
    public Long saveBasicBodyInfo(Long userId, BasicBodyInfoPostReq req) {
        BasicBodyInfo basicBodyInfo = BasicBodyInfo.builder()
                .userId(userId)
                .weight(req.getWeight())
                .height(req.getHeight())
                .bmi(req.getBmi())
                .bmr(req.getBmr())
                .build();

        return basicBodyInfoRepository.save(basicBodyInfo).getId();
    }

//    최신 기록 조회
    public LastestBodyCompositionGetRes getLastestBodyComposition(Long userId) {
        Optional<BodyComposition> optionalBodyComposition = bodyCompositionRepository.findTopByUserIdOrderByCreatedAtDesc(userId);

        if (optionalBodyComposition.isPresent()) {

            BodyComposition bodyComposition = optionalBodyComposition.get();

            LastestBodyCompositionGetRes res = LastestBodyCompositionGetRes.builder()

                    .bmi(bodyComposition.getBmi())
                    .createdAt(bodyComposition.getCreatedAt())
                    .percentBodyFat(bodyComposition.getPercentBodyFat())
                    .height(bodyComposition.getHeight())
                    .weight(bodyComposition.getWeight())
                    .skeletalMuscleMass(bodyComposition.getSkeletalMuscleMass())
                    .measuredId(bodyComposition.getMeasuredId())
                    .userId(userId)
                    .build();
            return res;
        } else {

            return LastestBodyCompositionGetRes.builder()
                    .userId(userId)
                    .bmi(0.0)
                    .percentBodyFat(0.0)
                    .height(0.0)
                    .weight(0.0)
                    .skeletalMuscleMass(0.0)
                    .build();
        }
    }

//    차트용 데이터 조회

public BodyCompositionSeriesGetRes getBodyCompositionSeries(Long userId, BodyCompositionSeriesGetReq req) {

    // 디바이스 타입 전체 선택하면 "All" 로 지정
    String deviceType = req.getDeviceType() != null ? req.getDeviceType() : "ALL";


    Optional<BodyComposition> optionalFirstRecord = bodyCompositionRepository
            .findTopByUserIdOrderByCreatedAtAsc(userId);
    // 받아 온 날짜 범위
    DateRangeDto range;
    List<BodyComposition> bodyCompositions;


    if (req.getRange() != null) {
        range = DateRangeDto.builder()
                .startDate(req.getRange().getStartDate())
                .endDate(req.getRange().getEndDate())
                .build();
    } else if (optionalFirstRecord.isPresent()) {

        BodyComposition firstRecord = optionalFirstRecord.get(); // Optional에서 안전하게 객체 추출
        range = DateRangeDto.builder()
                .startDate(firstRecord.getCreatedAt())
                .endDate(LocalDateTime.now())
                .build();
    } else {

        range = DateRangeDto.builder()
                .startDate(LocalDateTime.now().minusYears(1)) // 기본 시작 날짜 설정
                .endDate(LocalDateTime.now())
                .build();
    }

    if (optionalFirstRecord.isPresent() && "ALL".equalsIgnoreCase(deviceType)) {
        // 기록이 있고, ALL 타입일 때
        bodyCompositions = bodyCompositionRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        userId
                        , range.getStartDate()
                        , range.getEndDate()
                );
    } else if (optionalFirstRecord.isPresent()) {
        // 기록이 있고, 특정 타입일 때
        bodyCompositions = bodyCompositionRepository
                .findByUserIdAndDeviceTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        userId
                        , deviceType
                        , range.getStartDate()
                        , range.getEndDate()
                );
    } else {
        // 기록이 없는 경우: DB 조회를 생략하고 빈 리스트를 반환
        bodyCompositions = new ArrayList<>();
    }
    // 측정항목 조회 및 매핑
    List<BodyCompositionMetric> metrics = metricRepository.findAll();

    List<BodyCompositionPointDto> pointDto = bodyCompositions.stream()
            .map(item -> {
                Map<String, Double> values = new HashMap<>();
                for (BodyCompositionMetric metric : metrics) {
                    Double value = item.getValueByMetricCode(metric.getMetricCode());
                    values.put(metric.getMetricCode(), value);
                }
                return BodyCompositionPointDto.builder()
                        .date(item.getCreatedAt().toLocalDate())
                        .values(values)
                        .build();
            })
            .collect(Collectors.toList());


    BodyCompositionSeriesGetRes result = BodyCompositionSeriesGetRes.builder()
            .deviceType(deviceType)
            .points(pointDto)
            .range(range)
            .build();

    return result;
}

    // 체성분 기록 리스트 보기 (JPA Repository 사용)
    public List<BodyCompositionListGetRes> getBodyCompositionList(Long userId, BodyCompositionListGetReq req) {

        // 1. 요청 (req) 값이 없을 경우, DB에서 사용자 기록의 최소/최대 날짜를 조회하여 기본값 설정
        if (req.getStartDate() == null || req.getEndDate() == null) {
            MinMaxDateDto minMaxDates = bodyCompositionRepository.findMinMaxDatesByUserId(userId);

            if (minMaxDates != null) {
                if (req.getStartDate() == null) {
                    req.setStartDate(minMaxDates.getStartDate());
                }
                if (req.getEndDate() == null) {
                    req.setEndDate(minMaxDates.getEndDate());
                }
            }
        }

        // 2. 디바이스 타입 기본값 설정 (요청값이 null이거나 "ALL"일 경우 필터링하지 않도록 null로 설정)
        if (req.getDeviceType() == null || "ALL".equalsIgnoreCase(req.getDeviceType())) {
            req.setDeviceType(null);
        }

        // 3. 페이징 및 필터링 DTO 구성 (기존 로직 유지)
        BodyCompositionListGetDto dto = BodyCompositionListGetDto.builder()
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .size(req.getRowPerPage())
                .startIdx((req.getPage() - 1) * req.getRowPerPage())
                .deviceType(req.getDeviceType())
                .userId(userId)
                .build();

        // 4. ⭐ 목록 조회 (JPA 환경에 맞는 Custom Repository 메서드 호출로 변경 필요)
        // DTO를 받아 동적 쿼리를 처리하는 Custom Repository 메서드라고 가정합니다.
        // return bodyCompositionRepository.findByFiltersAndPagination(dto); // 예시

        // 현재는 기존 코드의 반환 형태를 유지하기 위해 임시로 기존 매퍼 이름을 사용합니다.
        // 실제 JPA 환경에서는 BodyCompositionRepository의 적절한 메서드로 대체해야 합니다.
        return bodyCompositionMapper.findByLimitTo(dto); // ⚠️ 이 부분은 실제 JPA 구현으로 변경해야 합니다.
    }


//    [GET] metrics
    public List<BodyCompositionMetric> getMetrics() {
        List<BodyCompositionMetric> metrics = metricRepository.findAll();
    return metrics;
    }

//    [GET] 기본 신체정보
    public List<BasicBodyInfoGetRes> getBasicBodyInfo(Long userId) {
       List<BasicBodyInfo> basicBodyInfo = basicBodyInfoRepository.findByUserId(userId);

        if (basicBodyInfo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원의 기본 신체 정보가 존재하지 않습니다.");
        }

        List<BasicBodyInfoGetRes> res = basicBodyInfo.stream()
                         .map(item -> {
                             return BasicBodyInfoGetRes.builder()
                                     .height(item.getHeight())
                                     .weight(item.getWeight())
                                     .bmi(item.getBmi())
                                     .bmr(item.getBmr())
                                     .build();
                         }).collect(Collectors.toList());

         return res;
    }
}
