package com.example.knavi.navigation.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.ContentDirectory;
import com.example.knavi.navigation.domain.KeywordTargetType;
import com.example.knavi.navigation.domain.NavigationKeyword;
import com.example.knavi.navigation.infrastructure.NavigationKeywordRepository;
import com.example.knavi.organization.ContactDirectory;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.text.TextNormalizer;
import com.example.knavi.shared.web.ApiErrors;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ナビゲーションキーワードの管理（コンテンツ担当者・システム管理者） */
@Service
@Transactional
public class NavigationKeywordService {

    private final NavigationKeywordRepository keywords;
    private final ContentDirectory content;
    private final ContactDirectory contacts;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;

    NavigationKeywordService(
            NavigationKeywordRepository keywords,
            ContentDirectory content,
            ContactDirectory contacts,
            AccessPolicy accessPolicy,
            AuditLog auditLog) {
        this.keywords = keywords;
        this.content = content;
        this.contacts = contacts;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<NavigationKeyword> findByTarget(KeywordTargetType type, UUID targetId, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        return keywords.findByTargetTypeAndTargetIdOrderByNormalizedTerm(type, targetId);
    }

    /** 利用者が入力しそうな言葉で、どこに案内されるかを確認する（辞書の調整用） */
    @Transactional(readOnly = true)
    public List<NavigationKeyword> findByTerm(String term, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        return keywords.findByNormalizedTermOrderByWeightDesc(TextNormalizer.normalize(term));
    }

    public NavigationKeyword add(
            String term, KeywordTargetType type, UUID targetId, float weight, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        String normalized = TextNormalizer.normalize(term);
        if (normalized.isEmpty()) {
            throw ApiErrors.badRequest("キーワードを入力してください。");
        }
        boolean exists = type == KeywordTargetType.CONTACT
                ? contacts.findById(targetId).isPresent()
                : content.exists(type.name(), targetId);
        if (!exists) {
            throw ApiErrors.badRequest("案内先（" + type + " " + targetId + "）が存在しません。");
        }
        if (keywords.existsByNormalizedTermAndTargetTypeAndTargetId(normalized, type, targetId)) {
            throw ApiErrors.conflict("同じキーワードが既に登録されています。");
        }
        NavigationKeyword keyword = keywords.save(new NavigationKeyword(term, type, targetId, weight));
        auditLog.record(
                user.id(), "NAVIGATION_KEYWORD_ADDED", "NAVIGATION_KEYWORD", keyword.getId(), Map.of("term", term));
        return keyword;
    }

    public void delete(UUID id, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        NavigationKeyword keyword = keywords.findById(id).orElseThrow(() -> ApiErrors.notFound("キーワードが見つかりません: " + id));
        keywords.delete(keyword);
        auditLog.record(
                user.id(), "NAVIGATION_KEYWORD_DELETED", "NAVIGATION_KEYWORD", id, Map.of("term", keyword.getTerm()));
    }
}
