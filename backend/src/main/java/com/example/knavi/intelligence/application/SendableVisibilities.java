package com.example.knavi.intelligence.application;

import java.util.List;
import java.util.Set;

/** プロバイダに送ってよい公開範囲（ADR-0008）。Embedding と回答文の生成で同じ規則を使う */
final class SendableVisibilities {

    /** 社外に送らないプロバイダ（Ollama など）に送ってよい公開範囲。CONFIDENTIAL はどのプロバイダにも送らない */
    static final Set<String> LOCAL = Set.of("INTERNAL", "MANAGERS", "RESTRICTED");

    private SendableVisibilities() {}

    /**
     * @param external プロバイダが文章を社外に送るか（ai-service の /v1/info）
     * @param configured 社外に送るときに許す公開範囲（knavi.ai.external-visibilities）
     */
    static Set<String> of(boolean external, List<String> configured) {
        if (!external) {
            return LOCAL;
        }
        return Set.copyOf((configured == null ? List.<String>of() : configured)
                .stream().filter(LOCAL::contains).toList());
    }
}
