package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.BetTemplate;
import com.fantacalcio.fantaschedina.domain.enums.OutcomeType;
import com.fantacalcio.fantaschedina.dto.BetTemplateForm;
import com.fantacalcio.fantaschedina.dto.BetTemplateRowRequest;
import com.fantacalcio.fantaschedina.dto.PickSlot;
import com.fantacalcio.fantaschedina.repository.BetTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BetTemplateService {

    private final BetTemplateRepository betTemplateRepository;

    @Transactional(readOnly = true)
    public List<BetTemplate> findByLeague(Long leagueId) {
        return betTemplateRepository.findByLeagueIdOrderByOrderIndexAsc(leagueId);
    }

    /**
     * Builds a BetTemplateForm pre-populated with existing config,
     * with one row per OutcomeType in a fixed display order.
     */
    @Transactional(readOnly = true)
    public BetTemplateForm buildForm(Long leagueId) {
        Map<OutcomeType, BetTemplate> existing = findByLeague(leagueId).stream()
            .collect(Collectors.toMap(BetTemplate::getOutcomeType, bt -> bt));

        List<BetTemplateRowRequest> rows = new ArrayList<>();
        for (OutcomeType type : OutcomeType.values()) {
            BetTemplateRowRequest row = new BetTemplateRowRequest();
            row.setOutcomeType(type);
            if (existing.containsKey(type)) {
                BetTemplate bt = existing.get(type);
                row.setRequiredCount(bt.getRequiredCount());
                row.setOverUnderThreshold(bt.getOverUnderThreshold());
            } else {
                row.setRequiredCount(0);
                row.setOverUnderThreshold(type == OutcomeType.OVER_UNDER ? 2.5 : null);
            }
            rows.add(row);
        }

        BetTemplateForm form = new BetTemplateForm();
        form.setRows(rows);
        return form;
    }

    @Transactional(readOnly = true)
    public List<PickSlot> buildPickSlots(Long leagueId) {
        List<BetTemplate> templates = findByLeague(leagueId);
        List<PickSlot> slots = new ArrayList<>();
        int idx = 0;
        for (BetTemplate t : templates) {
            for (int i = 0; i < t.getRequiredCount(); i++) {
                slots.add(new PickSlot(idx++, t));
            }
        }
        return slots;
    }

    public void save(Long leagueId, BetTemplateForm form) {
        log.debug("save: league {}", leagueId);
        betTemplateRepository.deleteByLeagueId(leagueId);

        int orderIndex = 0;
        for (BetTemplateRowRequest row : form.getRows()) {
            if (row.getRequiredCount() != null && row.getRequiredCount() > 0) {
                BetTemplate bt = BetTemplate.builder()
                    .leagueId(leagueId)
                    .outcomeType(row.getOutcomeType())
                    .requiredCount(row.getRequiredCount())
                    .overUnderThreshold(
                        row.getOutcomeType() == OutcomeType.OVER_UNDER
                            ? row.getOverUnderThreshold()
                            : null
                    )
                    .orderIndex(orderIndex++)
                    .build();
                betTemplateRepository.save(bt);
            }
        }
        log.info("save: league {} bet template saved ({} row(s))", leagueId, orderIndex);
    }
}
