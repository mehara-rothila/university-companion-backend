package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.FinancialAidRepository;
import com.smartuniversity.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The signed-in student's own financial-aid applications. Admin notes, the personal story and
 * supporting documents are left out: the assistant only needs the status, amounts and dates.
 */
@Component
public class MyFinancialAidTool implements AssistantTool {

    static final int MAX_RESULTS = 10;

    private final FinancialAidRepository financialAidRepository;
    private final UserRepository userRepository;

    public MyFinancialAidTool(FinancialAidRepository financialAidRepository, UserRepository userRepository) {
        this.financialAidRepository = financialAidRepository;
        this.userRepository = userRepository;
    }

    @Override
    public String name() {
        return "get_my_financial_aid_applications";
    }

    @Override
    public String description() {
        return "Lists the signed-in student's own financial-aid applications with their status, amounts and dates, "
                + "newest first. Use it when they ask about their application or aid status.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of();
    }

    @Override
    public Object run(JsonNode args, long userId) {
        Optional<User> user = userRepository.findById(userId);
        List<FinancialAid> applications = user.map(financialAidRepository::findByApplicant).orElse(List.of()).stream()
                .sorted(Comparator.comparing(FinancialAid::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", applications.size());
        result.put("applications", applications.stream().limit(MAX_RESULTS).map(MyFinancialAidTool::describe).toList());
        if (applications.isEmpty()) {
            result.put("note", "The student has no financial-aid applications. They can apply on Athena's Financial Aid page.");
        }
        return result;
    }

    private static Map<String, Object> describe(FinancialAid application) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", application.getTitle());
        ToolArgs.putIfPresent(details, "type", application.getAidType() == null ? null : application.getAidType().name());
        ToolArgs.putIfPresent(details, "category", application.getCategory());
        ToolArgs.putIfPresent(details, "status", application.getStatus() == null ? null : application.getStatus().name());
        ToolArgs.putIfPresent(details, "requested_amount", amount(application.getRequestedAmount()));
        ToolArgs.putIfPresent(details, "approved_amount", amount(application.getApprovedAmount()));
        if (application.isDonationEligible()) {
            ToolArgs.putIfPresent(details, "raised_from_donations", amount(application.getRaisedAmount()));
        }
        ToolArgs.putIfPresent(details, "applied", ToolArgs.when(application.getCreatedAt()));
        ToolArgs.putIfPresent(details, "deadline", ToolArgs.when(application.getApplicationDeadline()));
        ToolArgs.putIfPresent(details, "reviewed", ToolArgs.when(application.getReviewedAt()));
        if (application.getStatus() == FinancialAid.ApplicationStatus.REJECTED) {
            ToolArgs.putIfPresent(details, "rejection_reason", ToolArgs.clip(application.getRejectionReason(), 300));
        }
        return details;
    }

    private static String amount(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }
}
