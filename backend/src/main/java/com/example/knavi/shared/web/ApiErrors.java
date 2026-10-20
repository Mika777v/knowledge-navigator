package com.example.knavi.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** API のエラー。RFC 9457 の Problem Details として返る */
public final class ApiErrors {

    private ApiErrors() {}

    public static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, detail);
    }

    /** 業務ルールに反する操作（公開済みの版の編集など） */
    public static ErrorResponseException conflict(String detail) {
        return of(HttpStatus.CONFLICT, detail);
    }

    public static ErrorResponseException badRequest(String detail) {
        return of(HttpStatus.BAD_REQUEST, detail);
    }

    /** 楽観ロック: If-Match の版と現在の版が異なる（他の人が先に更新した） */
    public static ErrorResponseException preconditionFailed() {
        return of(HttpStatus.PRECONDITION_FAILED, "他のユーザーが先に更新しました。最新の内容を読み込んでから再度操作してください。");
    }

    public static ErrorResponseException preconditionRequired() {
        return of(HttpStatus.PRECONDITION_REQUIRED, "If-Match ヘッダーに現在の版（ETag）を指定してください。");
    }

    private static ErrorResponseException of(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
