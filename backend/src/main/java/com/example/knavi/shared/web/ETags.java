package com.example.knavi.shared.web;

/** 楽観ロック用の ETag（row_version を引用符で囲んだもの）と If-Match の変換 */
public final class ETags {

    private ETags() {}

    public static String of(int rowVersion) {
        return "\"" + rowVersion + "\"";
    }

    /** If-Match の値（"3" / W/"3" / 3）から版番号を取り出す。指定がなければ 428、不正な形式なら 412 */
    public static int parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiErrors.preconditionRequired();
        }
        String value = ifMatch.strip().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw ApiErrors.preconditionFailed();
        }
    }
}
