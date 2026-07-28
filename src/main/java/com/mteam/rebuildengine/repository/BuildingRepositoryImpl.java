package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// HELP6 §5: 조건이 많고 동적으로 바뀌는 검색은 Criteria API로 구현한다 — 파라미터마다 타입이 명시돼
// PostgreSQL 파라미터 타입 추론 문제(네이티브 쿼리에서 겪음)도 애초에 발생하지 않는다.
@Repository
public class BuildingRepositoryImpl implements BuildingRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<BuildingEntity> search(BuildingSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<BuildingEntity> query = cb.createQuery(BuildingEntity.class);
        Root<BuildingEntity> root = query.from(BuildingEntity.class);
        query.select(root).where(buildPredicate(cb, root, criteria));
        if (pageable.getSort().isSorted()) {
            query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));
        }

        List<BuildingEntity> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(content, pageable, count(cb, criteria));
    }

    private long count(CriteriaBuilder cb, BuildingSearchCriteria criteria) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<BuildingEntity> root = countQuery.from(BuildingEntity.class);
        countQuery.select(cb.count(root)).where(buildPredicate(cb, root, criteria));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private static Predicate buildPredicate(CriteriaBuilder cb, Root<BuildingEntity> root, BuildingSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("sggCdNm"), criteria.sggNm()));
        if (criteria.bjdongNm() != null) {
            predicates.add(cb.equal(root.get("stdgCdNm"), criteria.bjdongNm()));
        }
        predicates.add(cb.isFalse(root.get("isDeleted")));
        if (criteria.useApprovalDateMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("useAprvYmd"), criteria.useApprovalDateMin()));
        }
        if (criteria.useApprovalDateMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("useAprvYmd"), criteria.useApprovalDateMax()));
        }
        Predicate propertyTypeFiltersPredicate = buildPropertyTypeFiltersPredicate(cb, root, criteria.propertyTypeFilters());
        if (propertyTypeFiltersPredicate != null) {
            predicates.add(propertyTypeFiltersPredicate);
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    // 비어 있으면 유형/면적 제한 없음(§2.4 "전체"). 그 외엔 목록의 필터 중 하나라도 맞으면 포함(OR) —
    // 각 필터는 "그 유형이면서 그 유형에 지정된 면적 범위 안"(AND)이라 유형별로 다른 면적 단위를 담는다.
    private static Predicate buildPropertyTypeFiltersPredicate(CriteriaBuilder cb, Root<BuildingEntity> root,
                                                                 List<PropertyTypeAreaFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Predicate> orPredicates = filters.stream()
                .map(filter -> buildSingleFilterPredicate(cb, root, filter))
                .toList();
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildSingleFilterPredicate(CriteriaBuilder cb, Root<BuildingEntity> root, PropertyTypeAreaFilter filter) {
        return switch (filter.type()) {
            // 아파트/연립다세대는 세대당 추정 면적(gfa/hh_cnt) 기준으로 비교한다(§2.1-a, 2026-07-28 결정)
            // — building.gfa(동 전체 면적) 그대로 쓰면 "84㎡ 아파트를 찾는다"는 사용자 기대와 안 맞는다.
            case APARTMENT -> buildHouseholdAreaFilterPredicate(cb, root, filter, true);
            case ROW_HOUSE -> buildHouseholdAreaFilterPredicate(cb, root, filter, false);
            // 오피스텔은 건축물대장에 매핑 근거가 없어 항상 거짓 — 없으면 없게, 근사하지 않는다(§0-D).
            case OFFICETEL -> cb.disjunction();
            case SINGLE_FAMILY, COMMERCIAL, INDUSTRIAL -> buildUsageNameAreaFilterPredicate(cb, root, filter);
        };
    }

    private static Predicate buildHouseholdAreaFilterPredicate(CriteriaBuilder cb, Root<BuildingEntity> root,
                                                                 PropertyTypeAreaFilter filter, boolean apartment) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("mnUsgCdNm"), PropertyTypeClassifier.MULTI_FAMILY_USAGE_NAME));
        predicates.add(apartment
                ? cb.greaterThanOrEqualTo(root.get("grndNofl"), PropertyTypeClassifier.APARTMENT_MIN_FLOORS)
                : cb.lessThan(root.get("grndNofl"), PropertyTypeClassifier.APARTMENT_MIN_FLOORS));
        if (filter.areaMin() != null || filter.areaMax() != null) {
            // 세대당 면적을 실제로 비교할 때만 hh_cnt를 요구한다 — 유형만 선택하고 면적 조건이 없으면
            // hh_cnt 없는 건물(아파트 0.4%·연립다세대 17.4%)도 그대로 포함한다.
            predicates.add(cb.isNotNull(root.get("hhCnt")));
            predicates.add(cb.notEqual(root.get("hhCnt"), 0));
            Expression<BigDecimal> areaPerUnit = cb.quot(root.get("gfa"), root.get("hhCnt")).as(BigDecimal.class);
            if (filter.areaMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(areaPerUnit, filter.areaMin()));
            }
            if (filter.areaMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(areaPerUnit, filter.areaMax()));
            }
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private static Predicate buildUsageNameAreaFilterPredicate(CriteriaBuilder cb, Root<BuildingEntity> root, PropertyTypeAreaFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(root.get("mnUsgCdNm").in(PropertyTypeClassifier.usageNamesFor(filter.type())));
        if (filter.areaMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("gfa"), filter.areaMin()));
        }
        if (filter.areaMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("gfa"), filter.areaMax()));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
