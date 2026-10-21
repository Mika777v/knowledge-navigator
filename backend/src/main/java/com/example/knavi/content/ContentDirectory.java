package com.example.knavi.content;

import java.util.UUID;

/** 他のモジュール（ナビゲーション辞書など）がコンテンツの存在を確認するための公開 API */
public interface ContentDirectory {

    /** @param type PROGRAM / LIFE_EVENT / DOCUMENT / FAQ */
    boolean exists(String type, UUID id);
}
