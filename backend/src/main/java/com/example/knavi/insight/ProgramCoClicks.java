package com.example.knavi.insight;

import java.util.List;
import java.util.UUID;

/**
 * 検索結果から同じ人があわせて開いた制度（推薦用の公開 API。E12-T5）。
 *
 * <p>少人数の行動が推測できないよう、{@link #MIN_USERS} 人以上（仮名化したユーザーの数）が開いたものだけを返す。
 * 返すのは制度の ID だけで、人数は返さない。閲覧権限での絞り込みは呼び出し側で行う。
 */
public interface ProgramCoClicks {

    int MIN_USERS = 3;

    /** @return あわせて開いた人の多い順 */
    List<UUID> coClickedWith(UUID programId, int limit);
}
