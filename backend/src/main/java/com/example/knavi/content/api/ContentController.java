package com.example.knavi.content.api;

import com.example.knavi.content.application.ContentReadService;
import com.example.knavi.content.application.ContentViews.CategoryNode;
import com.example.knavi.content.application.ContentViews.DocumentDetail;
import com.example.knavi.content.application.ContentViews.DocumentSummary;
import com.example.knavi.content.application.ContentViews.FaqDetail;
import com.example.knavi.content.application.ContentViews.FaqSummary;
import com.example.knavi.content.application.ContentViews.LifeEventDetail;
import com.example.knavi.content.application.ContentViews.LifeEventSummary;
import com.example.knavi.content.application.ContentViews.ProgramDetail;
import com.example.knavi.content.application.ContentViews.ProgramSummary;
import com.example.knavi.content.application.ContentViews.SectionDetail;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一般利用者向けの閲覧 API。ログインユーザーの閲覧範囲（公開範囲・所属・ロール）で絞り込んだ結果だけを返す。
 * 見えないものの詳細は 404（存在するかどうかも分からないようにする）。
 */
@Tag(name = "content", description = "文書・条文・制度・FAQ・きっかけの閲覧")
@RestController
@RequestMapping("/api/v1")
class ContentController {

    private final ContentReadService service;

    ContentController(ContentReadService service) {
        this.service = service;
    }

    @Operation(summary = "文書の一覧（カテゴリで絞り込み可。親カテゴリを指定すると子カテゴリも含む）")
    @GetMapping("/documents")
    List<DocumentSummary> documents(
            @RequestParam(required = false) String category, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listDocuments(AccessScope.of(user), category);
    }

    @Operation(summary = "文書の詳細（公開版の条文一覧）")
    @GetMapping("/documents/{id}")
    DocumentDetail document(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getDocument(AccessScope.of(user), id);
    }

    @Operation(summary = "条文（引用リンクの行き先。過去の版の条文も表示できる）")
    @GetMapping("/sections/{id}")
    SectionDetail section(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getSection(AccessScope.of(user), id);
    }

    @Operation(summary = "制度の一覧")
    @GetMapping("/programs")
    List<ProgramSummary> programs(
            @RequestParam(required = false) String category, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listPrograms(AccessScope.of(user), category);
    }

    @Operation(summary = "制度の詳細（根拠の条・窓口・関連 FAQ・きっかけ）")
    @GetMapping("/programs/{id}")
    ProgramDetail program(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getProgram(AccessScope.of(user), id);
    }

    @Operation(summary = "FAQ の一覧")
    @GetMapping("/faqs")
    List<FaqSummary> faqs(
            @RequestParam(required = false) String category, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listFaqs(AccessScope.of(user), category);
    }

    @Operation(summary = "FAQ の詳細（回答・窓口・関連する制度と条文）")
    @GetMapping("/faqs/{id}")
    FaqDetail faq(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getFaq(AccessScope.of(user), id);
    }

    @Operation(summary = "きっかけの一覧（ホーム画面の「きっかけから探す」）")
    @GetMapping("/life-events")
    List<LifeEventSummary> lifeEvents() {
        return service.listLifeEvents();
    }

    @Operation(summary = "きっかけの詳細（まず確認する制度・あわせて必要な手続き）")
    @GetMapping("/life-events/{code}")
    LifeEventDetail lifeEvent(@PathVariable String code, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.getLifeEvent(AccessScope.of(user), code);
    }

    @Operation(summary = "カテゴリのツリー")
    @GetMapping("/categories")
    List<CategoryNode> categories() {
        return service.categoryTree();
    }
}
