package com.example.knavi.content.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * きっかけ（結婚・出産・引越しなど）の管理（E06-T2）。きっかけは部署をまたぐため、どの部署のコンテンツ担当者でも編集できる。
 *
 * <p>きっかけには公開範囲がない。紐づける制度の閲覧可否は、利用者が見るときに制度ごとに判定する（ContentReadService）。
 * 検索で使う言葉（例: 入籍 → 結婚）はナビゲーションキーワード辞書で管理する。
 */
@Service
@Transactional
public class LifeEventAdminService {

    public enum Relation {
        /** まず確認する制度 */
        PRIMARY,
        /** あわせて必要になりやすい手続き */
        RELATED
    }

    public record ProgramLink(String programCode, Relation relation) {}

    public record Attributes(String name, String icon, String description, int sortOrder, List<ProgramLink> programs) {}

    public record LinkedProgram(String code, String name, Relation relation) {}

    public record LifeEventView(
            UUID id,
            String code,
            String name,
            String icon,
            String description,
            int sortOrder,
            List<LinkedProgram> programs,
            int rowVersion) {}

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;

    LifeEventAdminService(JdbcClient jdbc, AccessPolicy accessPolicy, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<LifeEventView> list(AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        return jdbc.sql("SELECT code FROM life_events ORDER BY sort_order, code").query(String.class).list().stream()
                .map(this::find)
                .toList();
    }

    @Transactional(readOnly = true)
    public LifeEventView get(String code, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        return find(code);
    }

    public LifeEventView create(String code, Attributes attributes, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        List<ResolvedLink> links = resolve(attributes.programs());
        if (exists(code)) {
            throw ApiErrors.conflict("きっかけ " + code + " は既にあります。");
        }
        UUID id = Ids.newId();
        jdbc.sql("""
                        INSERT INTO life_events (id, code, name, icon, description, sort_order)
                        VALUES (:id, :code, :name, :icon, :description, :sort)
                        """)
                .param("id", id)
                .param("code", code)
                .param("name", attributes.name().strip())
                .param("icon", blankToNull(attributes.icon()))
                .param("description", blankToNull(attributes.description()))
                .param("sort", attributes.sortOrder())
                .update();
        replacePrograms(id, links);
        auditLog.record(user.id(), "LIFE_EVENT_CREATED", "LIFE_EVENT", id, Map.of("code", code));
        return find(code);
    }

    /** 名前・説明・表示順と、紐づける制度をまとめて置き換える（楽観ロック） */
    public LifeEventView update(String code, int expectedRowVersion, Attributes attributes, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        List<ResolvedLink> links = resolve(attributes.programs());
        UUID id = jdbc.sql("""
                        UPDATE life_events SET name = :name, icon = :icon, description = :description, sort_order = :sort,
                          row_version = row_version + 1, updated_at = now()
                        WHERE code = :code AND row_version = :version
                        RETURNING id
                        """)
                .param("code", code)
                .param("version", expectedRowVersion)
                .param("name", attributes.name().strip())
                .param("icon", blankToNull(attributes.icon()))
                .param("description", blankToNull(attributes.description()))
                .param("sort", attributes.sortOrder())
                .query(UUID.class)
                .optional()
                .orElseThrow(() ->
                        exists(code) ? ApiErrors.preconditionFailed() : ApiErrors.notFound("きっかけが見つかりません: " + code));
        replacePrograms(id, links);
        auditLog.record(
                user.id(),
                "LIFE_EVENT_UPDATED",
                "LIFE_EVENT",
                id,
                Map.of(
                        "code",
                        code,
                        "programs",
                        links.stream().map(ResolvedLink::code).toList()));
        return find(code);
    }

    private record ResolvedLink(UUID programId, String code, Relation relation) {}

    /** 制度コードを ID にする。存在しない制度・重複・空の一覧は受け付けない */
    private List<ResolvedLink> resolve(List<ProgramLink> programs) {
        if (programs == null || programs.isEmpty()) {
            throw ApiErrors.badRequest("制度を1つ以上紐づけてください。");
        }
        if (programs.stream().noneMatch(p -> p.relation() == Relation.PRIMARY)) {
            throw ApiErrors.badRequest("「まず確認する制度」を1つ以上選んでください。");
        }
        Set<String> seen = new HashSet<>();
        List<ResolvedLink> resolved = new ArrayList<>();
        for (ProgramLink p : programs) {
            if (!seen.add(p.programCode())) {
                throw ApiErrors.badRequest("制度 " + p.programCode() + " が重複しています。");
            }
            UUID programId = jdbc.sql("SELECT id FROM programs WHERE code = :code")
                    .param("code", p.programCode())
                    .query(UUID.class)
                    .optional()
                    .orElseThrow(() -> ApiErrors.badRequest("制度 " + p.programCode() + " が存在しません。"));
            resolved.add(new ResolvedLink(programId, p.programCode(), p.relation()));
        }
        return resolved;
    }

    private void replacePrograms(UUID lifeEventId, List<ResolvedLink> links) {
        jdbc.sql("DELETE FROM life_event_programs WHERE life_event_id = :id")
                .param("id", lifeEventId)
                .update();
        for (int i = 0; i < links.size(); i++) {
            jdbc.sql("""
                            INSERT INTO life_event_programs (life_event_id, program_id, relation, sort_order)
                            VALUES (:event, :program, :relation, :sort)
                            """)
                    .param("event", lifeEventId)
                    .param("program", links.get(i).programId())
                    .param("relation", links.get(i).relation().name())
                    .param("sort", i)
                    .update();
        }
    }

    private LifeEventView find(String code) {
        record Row(UUID id, String code, String name, String icon, String description, int sortOrder, int rowVersion) {}
        Row row = jdbc.sql("""
                        SELECT id, code, name, icon, description, sort_order, row_version FROM life_events WHERE code = :code
                        """)
                .param("code", code)
                .query((rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("icon"),
                        rs.getString("description"),
                        rs.getInt("sort_order"),
                        rs.getInt("row_version")))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("きっかけが見つかりません: " + code));
        // 管理画面では、下書き・公開終了の制度も含めてすべて表示する（担当者が紐づけを確認できるように）
        Map<String, LinkedProgram> programs = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT p.code, p.name, lp.relation FROM life_event_programs lp JOIN programs p ON p.id = lp.program_id
                        WHERE lp.life_event_id = :id ORDER BY lp.relation, lp.sort_order
                        """)
                .param("id", row.id())
                .query((rs, i) -> new LinkedProgram(
                        rs.getString("code"), rs.getString("name"), Relation.valueOf(rs.getString("relation"))))
                .list()
                .forEach(p -> programs.put(p.code(), p));
        return new LifeEventView(
                row.id(),
                row.code(),
                row.name(),
                row.icon(),
                row.description(),
                row.sortOrder(),
                List.copyOf(programs.values()),
                row.rowVersion());
    }

    private boolean exists(String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM life_events WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
